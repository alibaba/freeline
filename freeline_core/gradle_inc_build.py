# -*- coding:utf8 -*-
from __future__ import print_function

import os
import shutil

import android_tools
from build_commands import CompileCommand, IncAaptCommand, IncJavacCommand, IncDexCommand
from builder import IncrementalBuilder, Builder
from gradle_tools import get_project_info, GradleDirectoryFinder, GradleSyncClient, GradleSyncTask, \
    GradleCleanCacheTask, GradleMergeDexTask, get_sync_native_file_path, fix_package_name, DataBindingProcessor, \
    DatabindingDirectoryLookUp
from task import find_root_tasks, find_last_tasks, Task
from utils import get_file_content, write_file_content, is_windows_system, cexec, load_json_cache, get_md5, \
    write_json_cache
from tracing import Tracing
from exceptions import FreelineException


class GradleIncBuilder(IncrementalBuilder):
    def __init__(self, changed_files, config, task_engine, project_info=None):
        IncrementalBuilder.__init__(self, changed_files, config, task_engine, builder_name="gradle_inc_builder")
        self._project_info = project_info
        self._tasks_dictionary = {}
        self._module_dependencies = {}
        self._all_modules = []
        self._is_art = False
        self._module_dir_map = {}
        self._has_res_changed = self.__is_any_modules_have_res_changed()
        self._changed_modules = self.__changed_modules()
        self._original_changed_files = dict(changed_files)

    def check_build_environment(self):
        if not self._project_info:
            self._project_info = get_project_info(self._config)

        self._all_modules = self._project_info.keys()

        for item in self._project_info.values():
            self._module_dir_map[item['name']] = item['relative_dir']

        for key, value in self._project_info.iteritems():
            self._module_dependencies[key] = [item for item in value['local_module_dep']]

        self._is_art = android_tools.get_device_sdk_version_by_adb(Builder.get_adb(self._config)) > 20
        # merge all resources modified files to main resources
        self.__merge_res_files()
        self.__merge_native_files()

    def generate_sorted_build_tasks(self):
        """
        sort build tasks according to the module's dependency
        :return: None
        """
        for module in self._all_modules:
            task = android_tools.AndroidIncrementalBuildTask(module, self.__setup_inc_command(module))
            self._tasks_dictionary[module] = task

        for module in self._all_modules:
            task = self._tasks_dictionary[module]
            for dep in self._module_dependencies[module]:
                task.add_parent_task(self._tasks_dictionary[dep])

    def __setup_inc_command(self, module):
        return GradleCompileCommand(module, self.__setup_invoker(module))

    def __setup_invoker(self, module):
        return GradleIncBuildInvoker(module, self._project_info[module]['path'], self._config,
                                     self._changed_files['projects'][module], self._project_info[module], self._is_art,
                                     all_module_info=self._project_info, module_dir_map=self._module_dir_map,
                                     is_any_modules_have_res_changed=self._has_res_changed,
                                     changed_modules=self._changed_modules)

    def __merge_res_files(self):
        main_res = self._changed_files['projects'][self._config['main_project_name']]
        for module, file_dict in self._changed_files['projects'].iteritems():
            if module == self._config['main_project_name']:
                continue
            for key, files in file_dict.iteritems():
                if key == 'res' or key == 'assets':
                    main_res[key].extend(files)
        self._changed_files['projects'][self._config['main_project_name']] = main_res

    def __merge_native_files(self):
        so_files = []
        for module, file_dict in self._changed_files['projects'].iteritems():
            for key, files in file_dict.iteritems():
                if key == 'so':
                    for m in range(len(files)):
                        self.debug('append {} to native queue'.format(files[m]))
                        so_files.append(files[m])

        if len(so_files) > 0:
            from zipfile import ZipFile
            with ZipFile(get_sync_native_file_path(self._config['build_cache_dir']), "w") as nativeZip:
                for m in range(len(so_files)):
                    nativeZip.write(so_files[m])

    def __is_any_modules_have_res_changed(self):
        for key, value in self._changed_files['projects'].iteritems():
            if len(value['res']) > 0:
                self.debug('find {} modules have res changed'.format(key))
                return True
        return False

    def __changed_modules(self):
        modules = []
        for module, file_dict in self._changed_files['projects'].iteritems():
            if len(file_dict['src']) > 0 or len(file_dict['res']) or len(file_dict['assets']) > 0:
                modules.append(module)
        return modules

    def incremental_build(self):
        merge_dex_task = GradleMergeDexTask(self._config['build_cache_dir'], self._all_modules, self._project_info)
        aapt_task = GradleAaptTask(self.__setup_invoker(self._config['main_project_name']),
                                   self._original_changed_files, self._changed_files)

        task_list = self._tasks_dictionary.values()
        last_tasks = find_last_tasks(task_list)

        for rtask in find_root_tasks(task_list):
            aapt_task.add_child_task(rtask)

        clean_cache_task = GradleCleanCacheTask(self._config['build_cache_dir'], self._project_info)
        sync_client = GradleSyncClient(self._is_art, self._config, self._project_info, self._all_modules)
        connect_task = android_tools.ConnectDeviceTask(sync_client)
        sync_task = GradleSyncTask(sync_client, self._config['build_cache_dir'])
        update_stat_task = android_tools.UpdateStatTask(self._config, self._changed_files['projects'])

        map(lambda task: task.add_child_task(merge_dex_task), last_tasks)
        connect_task.add_child_task(sync_task)
        merge_dex_task.add_child_task(sync_task)
        sync_task.add_child_task(clean_cache_task)
        clean_cache_task.add_child_task(update_stat_task)

        # self._task_engine.add_root_task(find_root_tasks(task_list))
        self._task_engine.add_root_task(aapt_task)
        self._task_engine.add_root_task(connect_task)
        self._task_engine.start()


class GradleAaptTask(Task):
    def __init__(self, invoker, original_changed_files, changed_files):
        Task.__init__(self, 'gradle_aapt_task')
        self._invoker = invoker
        self._original_changed_files = original_changed_files
        self._changed_files_ref = changed_files

    def execute(self):
        should_run_res_task = self._invoker.check_res_task()
        if not should_run_res_task:
            self.debug('no need to execute')
            return

        self.debug('start to execute aapt command...')
        self._invoker.fill_dependant_jars()
        self._invoker.check_ids_change()

        with Tracing("generate_id_keeper_files"):
            self._invoker.generate_r_file()

        # self._invoker.backup_res_files()
        with Tracing("incremental_databinding_process"):
            self._invoker.process_databinding(self._original_changed_files, self._changed_files_ref)

        with Tracing("run_incremental_aapt_task"):
            self._invoker.run_aapt_task()

        with Tracing("check_other_modules_resources"):
            self._invoker.check_other_modules_resources()

        self._invoker.recover_original_file_path()


class GradleCompileCommand(CompileCommand):
    def __init__(self, module, invoker):
        self._module = module
        CompileCommand.__init__(self, 'gradle_{}_compile_command'.format(module), invoker)

    def _setup(self):
        # self.add_command(GradleIncAaptCommand(self._module, self._invoker))
        self.add_command(GradleIncJavacCommand(self._module, self._invoker))
        self.add_command(GradleIncDexCommand(self._module, self._invoker))

    def execute(self):
        map(lambda command: command.execute(), self.command_list)


class GradleIncAaptCommand(IncAaptCommand):
    def __init__(self, module_name, invoker):
        IncAaptCommand.__init__(self, module_name, invoker)

    def execute(self):
        should_run_res_task = self._invoker.check_res_task()
        if not should_run_res_task:
            self.debug('no need to execute')
            return

        self.debug('start to execute aapt command...')
        self._invoker.fill_dependant_jars()
        self._invoker.check_ids_change()
        self._invoker.generate_r_file()
        # self._invoker.backup_res_files()
        self._invoker.run_aapt_task()


class GradleIncJavacCommand(IncJavacCommand):
    def __init__(self, module_name, invoker):
        IncJavacCommand.__init__(self, module_name, invoker)

    def execute(self):
        self._invoker.check_r_md5()  # check if R.java has changed
        # self._invoker.check_other_modules_resources()
        should_run_javac_task = self._invoker.check_javac_task()
        if not should_run_javac_task:
            self.debug('no need to execute')
            return

        self.debug('start to execute javac command...')
        self._invoker.append_r_file()
        self._invoker.fill_classpaths()
        self._invoker.fill_extra_javac_args()
        self._invoker.clean_dex_cache()
        self._invoker.run_apt_only()
        self._invoker.run_javac_task()
        self._invoker.run_retrolambda()


class GradleIncDexCommand(IncDexCommand):
    def __init__(self, module_name, invoker):
        IncDexCommand.__init__(self, module_name, invoker)

    def execute(self):
        should_run_dex_task = self._invoker.check_dex_task()
        if not should_run_dex_task:
            self.debug('no need to execute')
            return

        self.debug('start to execute dex command...')
        self._invoker.run_dex_task()


class GradleIncBuildInvoker(android_tools.AndroidIncBuildInvoker):
    def __init__(self, module_name, path, config, changed_files, module_info, is_art, all_module_info=None,
                 module_dir_map=None, is_any_modules_have_res_changed=False, changed_modules=None):
        android_tools.AndroidIncBuildInvoker.__init__(self, module_name, path, config, changed_files, module_info,
                                                      is_art=is_art)
        self._all_module_info = all_module_info
        self._module_dir_map = module_dir_map
        self._is_any_modules_have_res_changed = is_any_modules_have_res_changed
        self._changed_modules = changed_modules
        self._merged_res_paths = []
        self._merged_res_paths.append(self._finder.get_backup_res_dir())
        self._replace_mapper = {}
        self._is_retrolambda_enabled = 'retrolambda' in self._config and self._name in self._config['retrolambda'] \
                                       and self._config['retrolambda'][self._name]['enabled']
        self._is_databinding_enabled = 'databinding_modules' in self._config and self._name in self._config[
            'databinding_modules']
        self._is_dagger_enabled = 'apt_libraries' in self._config and self._config['apt_libraries']['dagger']
        self._apt_output_dir = None
        for mname in self._all_module_info.keys():
            if mname in self._config['project_source_sets']:
                self._merged_res_paths.extend(self._config['project_source_sets'][mname]['main_res_directory'])
                self._merged_res_paths.extend(self._config['project_source_sets'][mname]['main_assets_directory'])

    def before_execute(self):
        self._finder = GradleDirectoryFinder(self._name, self._module_path, self._cache_dir,
                                             package_name=self._module_info['packagename'], config=self._config)

    def check_res_task(self):
        if self._name != self._config['main_project_name']:
            self.debug('skip {} aapt task'.format(self._name))
            return False
        return android_tools.AndroidIncBuildInvoker.check_res_task(self)

    def fill_dependant_jars(self):
        self._res_dependencies = self._module_info['dep_jar_path']

    def process_databinding(self, original_changed_files, changed_files_ref):
        if 'databinding' in self._config:
            if self._config['databinding_modules'] == 0:
                self.debug('no modules for processing databinding')
                return

            databinding_config = self._config['databinding']
            DatabindingDirectoryLookUp.load_path_map(self._config['build_cache_dir'])
            procossor = DataBindingProcessor(self._config)
            for module_config in databinding_config:
                module_name = module_config['name']
                if module_name in original_changed_files['projects']:
                    resources_files = original_changed_files['projects'][module_config['name']]['res']
                    if len(resources_files) == 0:
                        self.debug('module {} has no resources files changed'.format(module_name))
                        continue

                    changed_files_map = {}
                    res_dirs = self._config['project_source_sets'][module_name]['main_res_directory']
                    # TODO: detect matches missing issue
                    for path in resources_files:
                        for rdir in res_dirs:
                            if path.startswith(rdir):
                                if rdir in changed_files_map:
                                    changed_files_map[rdir].append(path)
                                else:
                                    changed_files_map[rdir] = [path]
                                break

                    for rdir in changed_files_map.keys():
                        output_res_dir = DatabindingDirectoryLookUp.find_target_res_path(rdir)
                        output_java_dir = DatabindingDirectoryLookUp.find_target_java_path(rdir)
                        output_layoutinfo_dir = DatabindingDirectoryLookUp.get_merged_layoutinfo_dir(self._cache_dir)
                        if output_res_dir and output_java_dir and output_layoutinfo_dir:
                            changed_files_list = changed_files_map[rdir]
                            procossor.process_module_databinding(module_config, rdir, output_res_dir,
                                                                 output_layoutinfo_dir, output_java_dir,
                                                                 self._config['sdk_directory'],
                                                                 changed_files=changed_files_list)
                            # replace file path
                            for path in changed_files_list:
                                new_path = path.replace(rdir, output_res_dir)
                                self._merged_res_paths.append(output_res_dir)  # append new path prefix
                                self.debug('replace {} with output path: {}'.format(path, new_path))
                                self._replace_mapper[new_path] = path
                                self._changed_files['res'].remove(path)
                                self._changed_files['res'].append(new_path)

                            # mark java compiler
                            if os.path.exists(output_layoutinfo_dir):
                                has_layoutinfo = False
                                for name in os.listdir(output_layoutinfo_dir):
                                    if name.endswith('.xml'):
                                        has_layoutinfo = True
                                        break

                                if has_layoutinfo:
                                    info_file = os.path.join(output_java_dir, 'android', 'databinding', 'layouts',
                                                             'DataBindingInfo.java')
                                    if os.path.exists(info_file):
                                        append_files = [info_file]
                                        append_files.extend(procossor.extract_related_java_files(module_name,
                                                                                                 output_layoutinfo_dir))

                                        if 'apt' not in changed_files_ref['projects'][module_name]:
                                            changed_files_ref['projects'][module_name]['apt'] = []

                                        for fpath in append_files:
                                            self.debug('add {} to {} module'.format(fpath, module_name))
                                            changed_files_ref['projects'][module_name]['apt'].append(fpath)

                                        if not android_tools.is_src_changed(self._config['build_cache_dir']):
                                            android_tools.mark_src_changed(self._config['build_cache_dir'])

    def _get_aapt_args(self):
        aapt_args = [self._aapt, 'package', '-f', '-I',
                     os.path.join(self._config['compile_sdk_directory'], 'android.jar'),
                     '-M', fix_package_name(self._config, self._finder.get_dst_manifest_path())]

        for rdir in self._config['project_source_sets'][self._name]['main_res_directory']:
            if os.path.exists(rdir):
                aapt_args.append('-S')
                aapt_args.append(DatabindingDirectoryLookUp.find_target_res_path(rdir))

        for rdir in self._module_info['local_dep_res_path']:
            if os.path.exists(rdir):
                aapt_args.append('-S')
                aapt_args.append(DatabindingDirectoryLookUp.find_target_res_path(rdir))

        for resdir in self._module_info['dep_res_path']:
            if os.path.exists(resdir):
                aapt_args.append('-S')
                aapt_args.append(resdir)

        if 'extra_dep_res_paths' in self._config and self._config['extra_dep_res_paths'] is not None:
            arr = self._config['extra_dep_res_paths']
            for path in arr:
                path = path.strip()
                if os.path.isdir(path):
                    aapt_args.append('-S')
                    aapt_args.append(path)

        aapt_args.append('-S')
        aapt_args.append(self._finder.get_backup_res_dir())

        freeline_assets_dir = os.path.join(self._config['build_cache_dir'], 'freeline-assets')
        aapt_args.append('-A')
        aapt_args.append(freeline_assets_dir)

        for adir in self._config['project_source_sets'][self._name]['main_assets_directory']:
            if os.path.exists(adir):
                aapt_args.append('-A')
                aapt_args.append(adir)

        for adir in self._module_info['local_dep_assets_path']:
            if os.path.exists(adir):
                aapt_args.append('-A')
                aapt_args.append(adir)

        for adir in self._module_info['dep_assets_path']:
            if os.path.exists(adir):
                aapt_args.append('-A')
                aapt_args.append(adir)

        gen_path = self._finder.get_backup_dir()
        aapt_args.append('--custom-package')
        aapt_args.append(self._config['package'])
        aapt_args.append('-m')
        aapt_args.append('-J')
        aapt_args.append(gen_path)
        aapt_args.append('--auto-add-overlay')
        aapt_args.append('-P')
        aapt_args.append(self._finder.get_public_xml_path())

        final_changed_list = self._parse_changed_list()

        if is_windows_system():
            final_changed_list = [fpath.replace('\\', '/') for fpath in final_changed_list]

        final_changed_list_chain = ':'.join(final_changed_list)

        aapt_args.append('-F')
        aapt_args.append(self._finder.get_dst_res_pack_path(self._name))
        aapt_args.append('--debug-mode')
        aapt_args.append('--auto-add-overlay')
        aapt_args.append('--no-version-vectors')

        if len(final_changed_list_chain) > 0 and self._is_art:
            aapt_args.append('--buildIncrement')
            aapt_args.append(final_changed_list_chain)
            aapt_args.append('--resoucres-md5-cache-path')
            aapt_args.append(os.path.join(self._cache_dir, "arsc_cache.dat"))

        aapt_args.append('--ignore-assets')
        aapt_args.append('public_id.xml:public.xml:*.bak:.*')
        return aapt_args, final_changed_list

    def recover_original_file_path(self):
        copylist = list(self._changed_files['res'])
        for fpath in copylist:
            if fpath in self._replace_mapper:
                self._changed_files['res'].remove(fpath)
                self._changed_files['res'].append(self._replace_mapper[fpath])

    def check_other_modules_resources(self):
        if self._name == self._config['main_project_name'] and self._all_module_info is not None:
            changed_modules = self._changed_modules

            if len(changed_modules) > 0:
                self.__modify_main_r()

                for module in changed_modules:
                    fpath = self.__modify_other_modules_r(self._all_module_info[module]['packagename'])
                    self.debug('modify {}'.format(fpath))

    def __modify_main_r(self):
        main_r_fpath = os.path.join(self._finder.get_backup_dir(),
                                    self._module_info['packagename'].replace('.', os.sep), 'R.java')

        self.debug('modify {}'.format(main_r_fpath))
        buf = GradleIncBuildInvoker.remove_final_tag(get_file_content(main_r_fpath))
        buf = android_tools.fix_unicode_parse_error(buf, main_r_fpath)
        write_file_content(main_r_fpath, buf)

        target_main_r_dir = os.path.join(self.__get_freeline_backup_r_dir(),
                                         self._module_info['packagename'].replace('.', os.sep))
        if not os.path.exists(target_main_r_dir):
            os.makedirs(target_main_r_dir)

        target_main_r_path = os.path.join(target_main_r_dir, 'R.java')
        self.debug('copy {} to {}'.format(main_r_fpath, target_main_r_path))
        shutil.copy(main_r_fpath, target_main_r_path)

    def append_r_file(self):
        if self._name != self._config['main_project_name']:
            backupdir = self.__get_freeline_backup_r_dir()
            main_r_path = os.path.join(backupdir, self._config['package'].replace('.', os.sep), 'R.java')

            # main_r_path existence means that resource modification exists, so that need to add R.java to classpath
            if os.path.exists(main_r_path):
                pns = [self._config['package'], self._module_info['packagename']]

                for m in self._module_info['local_module_dep']:
                    pns.append(self._all_module_info[m]['packagename'])

                for pn in pns:
                    rpath = os.path.join(backupdir, pn.replace('.', os.sep), 'R.java')
                    if os.path.exists(rpath) and rpath not in self._changed_files['src']:
                        self._changed_files['src'].append(rpath)
                        self.debug('add R.java to changed list: ' + rpath)
                    elif pn == self._module_info['packagename']:
                        fpath = self.__modify_other_modules_r(pn)
                        self.debug('modify {}'.format(fpath))
                        if fpath and os.path.exists(fpath):
                            self._changed_files['src'].append(fpath)
                            self.debug('add R.java to changed list: ' + fpath)
        else:
            if is_windows_system():
                main_r_path = os.path.join(self._finder.get_backup_dir(),
                                           self._module_info['packagename'].replace('.', os.sep), 'R.java')
                if os.path.exists(main_r_path):
                    content = android_tools.fix_unicode_parse_error(get_file_content(main_r_path), main_r_path)
                    write_file_content(main_r_path, content)

    def fill_classpaths(self):
        # classpaths:
        # 1. patch classes
        # 2. dependent modules' patch classes
        # 3. android.jar
        # 4. third party jars
        # 5. generated classes in build directory
        patch_classes_cache_dir = self._finder.get_patch_classes_cache_dir()
        self._classpaths.append(patch_classes_cache_dir)
        self._classpaths.append(self._finder.get_dst_classes_dir())
        for module in self._module_info['local_module_dep']:
            finder = GradleDirectoryFinder(module, self._module_dir_map[module], self._cache_dir)
            self._classpaths.append(finder.get_patch_classes_cache_dir())

        # add main module classes dir to classpath to generate databinding files
        main_module_name = self._config['main_project_name']
        if self._name != main_module_name and self._is_databinding_enabled:
            finder = GradleDirectoryFinder(main_module_name, self._module_dir_map[main_module_name], self._cache_dir,
                                           config=self._config)
            self._classpaths.append(finder.get_dst_classes_dir())

        self._classpaths.append(os.path.join(self._config['compile_sdk_directory'], 'android.jar'))
        self._classpaths.extend(self._module_info['dep_jar_path'])

        # remove existing same-name class in build directory
        srcdirs = self._config['project_source_sets'][self._name]['main_src_directory']
        for dirpath, dirnames, files in os.walk(patch_classes_cache_dir):
            for fn in files:
                if self._is_r_file_changed and self._module_info['packagename'] + '.R.' in fn:
                    android_tools.delete_class(dirpath, fn.replace('.class', ''))
                if fn.endswith('.class') and '$' not in fn and 'R.' not in fn and 'Manifest.' not in fn:
                    cp = os.path.join(dirpath, fn)
                    java_src = cp.replace('.class', '.java').split('classes' + os.path.sep)[1]
                    existence = True
                    for src_dir in srcdirs:
                        if os.path.exists(os.path.join(src_dir, java_src)):
                            existence = True
                            break
                    if not existence:
                        android_tools.delete_class(dirpath, fn.replace('.class', ''))

    def fill_extra_javac_args(self):
        if 'apt' in self._config and self._name in self._config['apt'] and self._config['apt'][self._name]['enabled']:
            apt_config = self._config['apt'][self._name]
            self._apt_output_dir = apt_config['aptOutput']
            apt_args = ['-s', apt_config['aptOutput']]

            if apt_config['processor']:
                apt_args.append('-processor')
                apt_args.append(apt_config['processor'])

            if not apt_config['disableDiscovery']:
                apt_args.append('-processorpath')
                apt_args.append(apt_config['processorPath'])

            apt_args.extend(apt_config['aptArgs'])
            self._extra_javac_args.extend(apt_args)
        elif self._is_databinding_enabled:
            if self._name == self._config['main_project_name']:
                apt_output = os.path.join(self._config['build_directory'], 'generated', 'source', 'apt',
                                          self._config['product_flavor'], 'debug')
            else:
                apt_output = os.path.join(self._config['build_directory'], 'generated', 'source', 'apt', 'release')

            self._apt_output_dir = apt_output
            if not os.path.exists(apt_output):
                os.makedirs(apt_output)

            if self._config['databinding_compiler_jar'] != '':
                self.debug('add compiler jar to classpath: {}'.format(self._config['databinding_compiler_jar']))
                self._module_info['dep_jar_path'].append(self._config['databinding_compiler_jar'])

            apt_args = ['-s', apt_output, '-processorpath', os.pathsep.join(self._module_info['dep_jar_path'])]
            self._extra_javac_args.extend(apt_args)

    def run_apt_only(self):
        if self._is_databinding_enabled and self._should_run_databinding_apt():
            apt_args = self._generate_java_compile_args(extra_javac_args_enabled=True)
            self.debug('apt exec: ' + ' '.join(apt_args))
            output, err, code = cexec(apt_args, callback=None)

            if code != 0:
                raise FreelineException('apt compile failed.', '{}\n{}'.format(output, err))

            if self._apt_output_dir and os.path.exists(self._apt_output_dir):
                apt_cache_path = os.path.join(self._config['build_cache_dir'], 'apt_files_stat_cache.json')
                if os.path.exists(apt_cache_path):
                    apt_cache = load_json_cache(apt_cache_path)
                for dirpath, dirnames, files in os.walk(self._apt_output_dir):
                    for fn in files:
                        fpath = os.path.join(dirpath, fn)
                        if apt_cache and self._name in apt_cache:
                            if fpath in apt_cache[self._name]:
                                new_md5 = get_md5(fpath)
                                if new_md5 != apt_cache[self._name][fpath]['md5']:
                                    self.debug('detect new md5 value, add apt file to change list: {}'.format(fpath))
                                    self._changed_files['src'].append(fpath)
                            else:
                                self.debug('find new apt file, add to change list: {}'.format(fpath))
                                self._changed_files['src'].append(fpath)
                        else:
                            self.debug('apt cache not found, add to change list: {}'.format(fpath))
                            self._changed_files['src'].append(fpath)

    def run_javac_task(self):
        if self._is_only_r_changed() and not self._is_other_modules_has_src_changed:
            self._is_need_javac = False
            android_tools.clean_src_changed_flag(self._cache_dir)
            self.debug('apt process do not generate new files, ignore javac task.')
            return

        extra_javac_args_enabled = not (self._is_databinding_enabled and self._should_run_databinding_apt())
        javacargs = self._generate_java_compile_args(extra_javac_args_enabled=extra_javac_args_enabled)

        self.debug('javac exec: ' + ' '.join(javacargs))
        output, err, code = cexec(javacargs, callback=None)

        if code != 0:
            raise FreelineException('incremental javac compile failed.', '{}\n{}'.format(output, err))
        else:
            if self._is_r_file_changed:
                old_r_file = self._finder.get_dst_r_path(config=self._config)
                new_r_file = android_tools.DirectoryFinder.get_r_file_path(self._finder.get_backup_dir())
                if old_r_file and new_r_file:
                    shutil.copyfile(new_r_file, old_r_file)
                    self.debug('copy {} to {}'.format(new_r_file, old_r_file))

    def _should_run_databinding_apt(self):
        if 'apt' in self._changed_files:
            for fpath in self._changed_files['apt']:
                if fpath.endswith('DataBindingInfo.java'):
                    return True
        return False

    def _generate_java_compile_args(self, extra_javac_args_enabled=False):
        javacargs = [self._javac]
        arguments = ['-encoding', 'UTF-8', '-g']
        if not self._is_retrolambda_enabled:
            arguments.extend(['-target', '1.7', '-source', '1.7'])

        arguments.append('-cp')
        arguments.append(os.pathsep.join(self._classpaths))

        for fpath in self._changed_files['src']:
            arguments.append(fpath)

        if extra_javac_args_enabled:
            if 'apt' in self._changed_files:
                for fpath in self._changed_files['apt']:
                    arguments.append(fpath)

            filter_tags = []
            if self._is_databinding_enabled:
                filter_tags.extend(['BindingAdapter', 'BindingConversion', 'Bindable'])

            if self._is_dagger_enabled:
                filter_tags.extend(['DaggerComponent', 'DaggerModule'])

            files = self._get_apt_related_files(filter_tags=filter_tags)
            for fpath in files:
                if fpath and os.path.exists(fpath) and fpath not in self._changed_files['src']:
                    if 'apt' in self._changed_files and fpath in self._changed_files['apt']:
                        continue
                    self.debug('add apt related file: {}'.format(fpath))
                    arguments.append(fpath)

            arguments.extend(self._extra_javac_args)

        arguments.append('-d')
        arguments.append(self._finder.get_patch_classes_cache_dir())

        # ref: https://support.microsoft.com/en-us/kb/830473
        if is_windows_system():
            arguments_length = sum(map(len, arguments))
            if arguments_length > 8000:
                argument_file_path = os.path.join(self._finder.get_module_cache_dir(), 'javac_args_file')
                self.debug('arguments length: {} > 8000, save args to {}'.format(arguments_length, argument_file_path))

                if os.path.exists(argument_file_path):
                    os.remove(argument_file_path)

                arguments_content = ' '.join(arguments)
                self.debug('javac arguments: ' + arguments_content)
                write_file_content(argument_file_path, arguments_content)
                arguments = ['@{}'.format(argument_file_path)]

        javacargs.extend(arguments)
        return javacargs

    def _get_apt_related_files(self, filter_tags=None):
        path = self._get_apt_related_files_cache_path()
        if os.path.exists(path):
            return load_json_cache(path)
        else:
            info_path = os.path.join(self._cache_dir, 'freeline_annotation_info.json')
            if os.path.exists(info_path):
                info_cache = load_json_cache(info_path)
                related_files = []
                for anno, files in info_cache.iteritems():
                    if filter_tags and anno not in filter_tags:
                        self.debug('ignore annotation: {}'.format(anno))
                        continue

                    for info in files:
                        if 'java_path' in info and info['java_path']:
                            related_files.append(info['java_path'])
                write_json_cache(self._get_apt_related_files_cache_path(), related_files)
                return related_files
        return []

    def _append_new_related_files(self):
        related_files = self._get_apt_related_files()

        def append_files(file_list):
            for fpath in file_list:
                if fpath and fpath not in related_files:
                    self.debug('add new related file: {}'.format(fpath))
                    related_files.append(fpath)

        append_files(self._changed_files['src'])
        append_files(self._changed_files['apt'])
        write_json_cache(self._get_apt_related_files_cache_path(), related_files)

    def _get_apt_related_files_cache_path(self):
        return os.path.join(self._cache_dir, 'apt_related_files_cache.json')

    def run_retrolambda(self):
        if self._is_need_javac and self._is_retrolambda_enabled:
            lambda_config = self._config['retrolambda'][self._name]
            target_dir = self._finder.get_patch_classes_cache_dir()
            jar_args = [Builder.get_java(self._config),
                        '-Dretrolambda.inputDir={}'.format(target_dir),
                        '-Dretrolambda.outputDir={}'.format(target_dir)]

            if lambda_config['supportIncludeFiles']:
                files_stat_path = os.path.join(self._cache_dir, self._name, 'lambda_files_stat.json')

                include_files = []
                if os.path.exists(files_stat_path):
                    files_stat = load_json_cache(files_stat_path)
                else:
                    files_stat = {}

                for dirpath, dirnames, files in os.walk(target_dir):
                    for fn in files:
                        fpath = os.path.join(dirpath, fn)
                        if fpath not in files_stat:
                            include_files.append(fpath)
                            self.debug('incremental build new lambda file: {}'.format(fpath))
                        else:
                            if os.path.getmtime(fpath) > files_stat[fpath]['mtime']:
                                include_files.append(fpath)
                                self.debug('incremental build lambda file: {}'.format(fpath))

                include_files_param = os.pathsep.join(include_files)
                if len(include_files_param) > 3496:
                    include_files_path = os.path.join(self._cache_dir, self._name, 'retrolambda_inc.list')
                    self.__save_parms_to_file(include_files_path, include_files)
                    jar_args.append('-Dretrolambda.includedFile={}'.format(include_files_path))
                else:
                    jar_args.append('-Dretrolambda.includedFiles={}'.format(include_files_param))

            lambda_classpaths = [target_dir, lambda_config['rtJar']]
            lambda_classpaths.extend(self._classpaths)
            param = os.pathsep.join(lambda_classpaths)

            if lambda_config['supportIncludeFiles'] and len(param) > 3496:
                classpath_file = os.path.join(self._cache_dir, self._name, 'retrolambda_classpaths.path')
                self.__save_parms_to_file(classpath_file, lambda_classpaths)
                jar_args.append('-Dretrolambda.classpathFile={}'.format(classpath_file))
            else:
                jar_args.append('-Dretrolambda.classpath={}'.format(param))

            jar_args.append('-cp')
            jar_args.append(lambda_config['targetJar'])
            jar_args.append(lambda_config['mainClass'])

            self.debug('retrolambda exec: ' + ' '.join(jar_args))
            output, err, code = cexec(jar_args, callback=None)

            if code != 0:
                raise FreelineException('retrolambda compile failed.', '{}\n{}'.format(output, err))

            if lambda_config['supportIncludeFiles']:
                for fpath in include_files:
                    if fpath not in files_stat:
                        files_stat[fpath] = {}
                    files_stat[fpath]['mtime'] = os.path.getmtime(fpath)
                write_json_cache(files_stat_path, files_stat)
                self.debug('save lambda files stat to {}'.format(files_stat_path))

    def __save_parms_to_file(self, path, params):
        if os.path.exists(path):
            os.remove(path)
        content = ''
        for param in params:
            content += param + '\n'
        write_file_content(path, content)
        self.debug('save retrolambda params to {}'.format(path))

    def _get_res_incremental_dst_path(self, fpath):
        if 'assets' + os.sep in fpath:
            return os.path.join(self._finder.get_base_gen_dir(), 'assets', 'debug', fpath.split('assets' + os.sep)[1])
        elif 'res' + os.sep in fpath:
            return os.path.join(self._finder.get_res_dir(), fpath.split('res' + os.sep)[1])

    def _parse_changed_list(self):
        changed_list = []
        for rfile in self._changed_files['res']:
            if rfile not in changed_list:
                changed_list.append(self._get_res_relative_path(rfile))

        for afile in self._changed_files['assets']:
            if afile not in changed_list:
                changed_list.append(self._get_res_relative_path(afile))
        return changed_list

    def _get_res_relative_path(self, res):
        if res.startswith('res') or res.startswith('AndroidManifest.xml'):
            return res

        def path_fix(path):
            return path if path.endswith(os.sep) else path + os.sep

        for respath in self._merged_res_paths:
            respath = path_fix(respath)
            if res.startswith(respath):
                index = respath.strip(os.sep).rfind(os.sep)
                if index >= 0:
                    res_dir_name = respath[index + 1:].strip(os.sep)
                    relative_path = os.path.join(res_dir_name, res.replace(respath, ''))
                    self.debug("find relative path: {}".format(relative_path))
                    return relative_path
        self.debug('relative path not found: {}'.format(res))
        return None

    def __get_freeline_backup_r_dir(self):
        dirpath = os.path.join(self._cache_dir, 'freeline-backup-r')
        if not os.path.exists(dirpath):
            os.makedirs(dirpath)
        return dirpath

    def __modify_other_modules_r(self, package_name, finder=None):
        if not finder:
            finder = self._finder

        r_path = android_tools.find_r_file(finder.get_dst_r_dir(), package_name=package_name)
        if r_path and os.path.exists(r_path):
            target_dir = os.path.join(self.__get_freeline_backup_r_dir(), package_name.replace('.', os.sep))
            if not os.path.exists(target_dir):
                os.makedirs(target_dir)
            target_path = os.path.join(target_dir, 'R.java')
            if not os.path.exists(target_path):
                self.debug('copy {} to {}'.format(r_path, target_path))
                shutil.copy(r_path, target_path)

                content = get_file_content(target_path)
                content = GradleIncBuildInvoker.remove_final_tag(content)
                content = GradleIncBuildInvoker.extend_main_r(content, self._config['package'])
                content = android_tools.fix_unicode_parse_error(content, target_path)
                write_file_content(target_path, content)

            return target_path

    def __find_res_in_which_module(self, res_path):
        for module in self._all_module_info.keys():
            # rdir = android_tools.get_res_dir(module)
            res_dirs = self._config['project_source_sets'][module]['main_res_directory']
            for rdir in res_dirs:
                if rdir is not None:
                    if res_path.startswith(rdir) or rdir in res_path:
                        return module
        return None

    @staticmethod
    def remove_final_tag(content):
        content = content.replace('public final class', 'public class').replace('public static final class',
                                                                                'public static class')
        return content

    @staticmethod
    def extend_main_r(content, main_package_name):
        import re
        result = re.findall(r'''public static class (.*) \{''', content)
        for tag in result:
            content = content.replace('class ' + tag + ' {',
                                      'class ' + tag + ' extends ' + main_package_name + '.R.' + tag + ' {')
        return content
