# -*- coding:utf8 -*-
import os
import re

import android_tools
from utils import get_file_content
from freeline_build import ScanChangedFilesCommand, DispatchPolicy
from logger import Logger
from sync_client import SyncClient
from exceptions import FreelineException, FileMissedException
from utils import curl, write_json_cache, load_json_cache, cexec, print_json
from task import Task
from builder import Builder


class GradleScanChangedFilesCommand(ScanChangedFilesCommand):
    def __init__(self, config):
        ScanChangedFilesCommand.__init__(self)
        self._config = config
        self._changed_files = {}
        self.project_info = None
        self._stat_cache = None
        self._finder = GradleDirectoryFinder(self._config['main_project_name'], self._config['main_project_dir'],
                                             self._config['build_cache_dir'])

    def execute(self):
        cache_path = os.path.join(self._config['build_cache_dir'], 'stat_cache.json')
        if not os.path.exists(cache_path):
            raise FileMissedException('{} not found.'.format(cache_path), '     re-run clean build.')

        self._stat_cache = load_json_cache(cache_path)

        project_info_cache_path = os.path.join(self._config['build_cache_dir'], 'project_info_cache.json')
        if os.path.exists(project_info_cache_path):
            self.project_info = load_json_cache(project_info_cache_path)
        else:
            self.project_info = get_project_info(self._config)
            write_json_cache(project_info_cache_path, self.project_info)

        build_info = self._get_build_info()

        for module_name, module_info in self.project_info.iteritems():
            if module_name in self._stat_cache:
                self._changed_files[module_name] = {'libs': [], 'assets': [], 'res': [], 'src': [], 'manifest': [],
                                                    'config': [], 'so': [], 'cpp': []}
                self._scan_module_changes(module_name, module_info['path'])

        self._mark_changed_flag()

        return {'projects': self._changed_files, 'build_info': build_info}

    def _mark_changed_flag(self):
        info = self._changed_files.values()
        cache_dir = self._config['build_cache_dir']
        for bundle in info:
            if not android_tools.is_src_changed(cache_dir) and len(bundle['src']) > 0:
                android_tools.mark_src_changed(cache_dir)
            if not android_tools.is_res_changed(cache_dir) and len(bundle['res']) > 0:
                android_tools.mark_res_changed(cache_dir)

    def _get_build_info(self):
        final_apk_path = self._config['apk_path']
        last_clean_build_time = os.path.getmtime(final_apk_path) if os.path.exists(final_apk_path) else 0
        is_root_config_changed = os.path.getmtime(os.path.join('build.gradle')) > last_clean_build_time
        if not is_root_config_changed:
            is_root_config_changed = os.path.getmtime(os.path.join('settings.gradle')) > last_clean_build_time

        return {'last_clean_build_time': last_clean_build_time, 'is_root_config_changed': is_root_config_changed}

    def _scan_module_changes(self, module_name, module_path):
        module_cache = self._stat_cache[module_name]

        # scan bulid.gradle
        config_path = os.path.join(module_path, 'build.gradle')
        if self.__check_changes(module_name, config_path, module_cache):
            self._changed_files[module_name]['config'].append(config_path)

        # scan libs dirs
        libs_dir_names = ['libs', 'lib']
        for lib_dir_name in libs_dir_names:
            lib_dir_path = os.path.join(module_path, lib_dir_name)
            if os.path.isdir(lib_dir_path):
                for dirpath, dirnames, files in os.walk(lib_dir_path):
                    for fn in files:
                        if fn.endswith(".jar"):
                            fpath = os.path.join(dirpath, fn)
                            if self.__check_changes(module_name, fpath, module_cache):
                                self._changed_files[module_name]['libs'].append(fpath)

        if module_name in self._config['project_source_sets']:
            # scan manifest
            manifest = self._config['project_source_sets'][module_name]['main_manifest_path']
            if self.__check_changes(module_name, manifest, module_cache):
                self._changed_files[module_name]['manifest'].append(manifest)

            # scan native so
            if 'main_jniLibs_directory' in self._config['project_source_sets'][module_name]:
                native_so_dirs = self._config['project_source_sets'][module_name]['main_jniLibs_directory']
                for native_so_dir in native_so_dirs:
                    if os.path.exists(native_so_dir):
                        for dirpath, dirnames, files in os.walk(native_so_dir):
                            for fn in files:
                                if fn.endswith(".so"):
                                    fpath = os.path.join(dirpath, fn)
                                    if self.__check_changes(module_name, fpath, module_cache):
                                        self._changed_files[module_name]['so'].append(fpath)

            # scan assets
            assets_dirs = self._config['project_source_sets'][module_name]['main_assets_directory']
            for assets_dir in assets_dirs:
                if os.path.exists(assets_dir):
                    for dirpath, dirnames, files in os.walk(assets_dir):
                        for fn in files:
                            fpath = os.path.join(dirpath, fn)
                            if self.__check_changes(module_name, fpath, module_cache):
                                self._changed_files[module_name]['assets'].append(fpath)

            # scan res
            res_dirs = self._config['project_source_sets'][module_name]['main_res_directory']
            for res_dir in res_dirs:
                if os.path.exists(res_dir):
                    for sub_dir in os.listdir(res_dir):
                        sub_dir_path = os.path.join(res_dir, sub_dir)
                        if os.path.isdir(sub_dir_path) and android_tools.is_res_sub_dir(sub_dir):
                            for fn in os.listdir(sub_dir_path):
                                if '.DS_Store' in fn:
                                    continue
                                fpath = os.path.join(sub_dir_path, fn)
                                if self.__check_changes(module_name, fpath, module_cache, should_check_size=True):
                                    self._changed_files[module_name]['res'].append(fpath)

            # scan src
            src_dirs = self._config['project_source_sets'][module_name]['main_src_directory']
            for src_dir in src_dirs:
                if os.path.exists(src_dir):
                    for dirpath, dirnames, files in os.walk(src_dir):
                        if re.findall(r'[/\\+]androidTest[/\\+]', dirpath) or '/.' in dirpath:
                            continue
                        for fn in files:
                            if fn.endswith('java'):
                                if fn.endswith('package-info.java') or fn.endswith('BuildConfig.java'):
                                    continue
                                fpath = os.path.join(dirpath, fn)
                                if self.__check_changes(module_name, fpath, module_cache):
                                    self._changed_files[module_name]['src'].append(fpath)

    def __check_changes(self, module_name, fpath, module_cache, should_check_size=False):
        if not fpath:
            return False

        if fpath not in module_cache:
            self.debug('find new file {}'.format(fpath))
            return True

        stat = module_cache[fpath]
        mtime = os.path.getmtime(fpath)
        if mtime > stat['mtime']:
            self.debug('find {} has modification.'.format(fpath))
            stat['mtime'] = mtime
            self._stat_cache[module_name][fpath] = stat
            return True

        if should_check_size:
            size = os.path.getsize(fpath)
            if size != stat['size']:
                self.debug('find {} has modification.'.format(fpath))
                stat['size'] = size
                self._stat_cache[module_name][fpath] = stat
                return True

        return False


class GenerateFileStatTask(Task):
    def __init__(self, config):
        Task.__init__(self, 'generate_file_stat_task')
        self._config = config
        self._stat_cache = {}

    def execute(self):
        if 'modules' in self._config:
            all_modules = self._config['modules']
        else:
            all_modules = get_all_modules(os.getcwd())

        for module in all_modules:
            self._stat_cache[module['name']] = {}
            self._save_module_stat(module['name'], module['path'])
        self._save_cache()

    def _save_cache(self):
        cache_path = os.path.join(self._config['build_cache_dir'], 'stat_cache.json')
        if os.path.exists(cache_path):
            os.remove(cache_path)
        write_json_cache(cache_path, self._stat_cache)

    def _save_module_stat(self, module_name, module_path):
        # scan bulid.gradle
        self.__save_stat(module_name, os.path.join(module_path, 'build.gradle'))

        # scan libs dirs
        libs_dir_names = ['libs', 'lib']
        for lib_dir_name in libs_dir_names:
            lib_dir_path = os.path.join(module_path, lib_dir_name)
            if os.path.isdir(lib_dir_path):
                for dirpath, dirnames, files in os.walk(lib_dir_path):
                    for fn in files:
                        if fn.endswith(".jar"):
                            self.__save_stat(module_name, os.path.join(dirpath, fn))

        # scan assets
        if module_name in self._config['project_source_sets']:
            # scan manifest
            self.__save_stat(module_name, self._config['project_source_sets'][module_name]['main_manifest_path'])

            # scan native so
            if 'main_jniLibs_directory' in self._config['project_source_sets'][module_name]:
                native_so_dirs = self._config['project_source_sets'][module_name]['main_jniLibs_directory']
                for native_so_dir in native_so_dirs:
                    if os.path.exists(native_so_dir):
                        for dirpath, dirnames, files in os.walk(native_so_dir):
                            for fn in files:
                                if fn.endswith(".so"):
                                    self.__save_stat(module_name, os.path.join(dirpath, fn))

            assets_dirs = self._config['project_source_sets'][module_name]['main_assets_directory']
            for assets_dir in assets_dirs:
                if os.path.exists(assets_dir):
                    for dirpath, dirnames, files in os.walk(assets_dir):
                        for fn in files:
                            self.__save_stat(module_name, os.path.join(dirpath, fn))

            res_dirs = self._config['project_source_sets'][module_name]['main_res_directory']
            for res_dir in res_dirs:
                if os.path.exists(res_dir):
                    for sub_dir in os.listdir(res_dir):
                        sub_dir_path = os.path.join(res_dir, sub_dir)
                        if os.path.isdir(sub_dir_path) and android_tools.is_res_sub_dir(sub_dir):
                            for fn in os.listdir(sub_dir_path):
                                if '.DS_Store' in fn:
                                    continue
                                self.__save_stat(module_name, os.path.join(sub_dir_path, fn))

            src_dirs = self._config['project_source_sets'][module_name]['main_src_directory']
            for src_dir in src_dirs:
                if os.path.exists(src_dir):
                    for dirpath, dirnames, files in os.walk(src_dir):
                        if re.findall(r'[/\\+]androidTest[/\\+]', dirpath) or '/.' in dirpath:
                            continue
                        for fn in files:
                            if fn.endswith('java'):
                                if fn.endswith('package-info.java') or fn.endswith('BuildConfig.java'):
                                    continue
                                self.__save_stat(module_name, os.path.join(dirpath, fn))

    def __save_stat(self, module, fpath):
        if fpath is not None and os.path.exists(fpath):
            self._stat_cache[module][fpath] = {'mtime': os.path.getmtime(fpath), 'size': os.path.getsize(fpath)}


class GradleDispatchPolicy(DispatchPolicy):
    def __init__(self):
        DispatchPolicy.__init__(self)

    def is_need_clean_build(self, config, file_changed_dict):
        last_apk_build_time = file_changed_dict['build_info']['last_clean_build_time']

        if last_apk_build_time == 0:
            Logger.debug('final apk not found, need a clean build.')
            return True

        if file_changed_dict['build_info']['is_root_config_changed']:
            Logger.debug('find root build.gradle changed, need a clean build.')
            return True

        file_count = 0
        need_clean_build_projects = set()

        for dir_name, bundle_dict in file_changed_dict['projects'].iteritems():
            count = len(bundle_dict['src'])
            Logger.debug('find {} has {} java files modified.'.format(dir_name, count))
            file_count += count

            if len(bundle_dict['config']) > 0 or len(bundle_dict['manifest']) > 0:
                need_clean_build_projects.add(dir_name)
                Logger.debug('find {} has build.gradle or manifest file modified.'.format(dir_name))

        is_need_clean_build = file_count > 20 or len(need_clean_build_projects) > 0

        if is_need_clean_build:
            if file_count > 20:
                Logger.debug(
                    'project has {}(>20) java files modified so that it need a clean build.'.format(file_count))
            else:
                Logger.debug('project need a clean build.')
        else:
            Logger.debug('project just need a incremental build.')

        return is_need_clean_build


class GradleDirectoryFinder(android_tools.DirectoryFinder):
    def __init__(self, module_name, module_path, cache_dir, package_name=None, config=None):
        android_tools.DirectoryFinder.__init__(self, module_name, cache_dir)
        self._module_name = module_name
        self._module_path = module_path
        self._package_name = package_name
        self._config = config
        if not self._package_name:
            self._package_name = ''

    def get_dst_manifest_path(self):
        if self._config is not None and 'product_flavor' in self._config:
            if self._module_name == self._config['main_project_name']:
                if self._config['product_flavor'] == '' or self._config['product_flavor'] == 'debug':
                    path = os.path.join(self.get_base_gen_dir(), 'manifests', 'full', 'debug', 'AndroidManifest.xml')
                else:
                    path = os.path.join(self.get_base_gen_dir(), 'manifests', 'full', self._config['product_flavor'],
                                        'debug', 'AndroidManifest.xml')
                if os.path.exists(path):
                    return path
        path = android_tools.find_manifest(os.path.join(self.get_base_gen_dir(), 'manifests'))
        if path and os.path.exists(path):
            Logger.debug("find manifest: {}".format(path))
            return path
        return android_tools.get_manifest_path(self._module_path)

    def get_dst_r_dir(self):
        return os.path.join(self.get_base_build_dir(), 'generated', 'source', 'r')

    def get_dst_r_path(self, config=None):
        if config is not None:
            if 'main_project_name' in config and self._module_name == config['main_project_name']:
                if 'main_r_path' in config:
                    path = config['main_r_path'].strip()
                    if os.path.isfile(path):
                        return path
        return android_tools.find_r_file(self.get_dst_r_dir(), package_name=self._package_name)

    def get_backup_r_path(self):
        return os.path.join(self.get_backup_dir(), self._package_name.replace('.', os.sep), 'R.java')

    def get_dst_res_dir(self):
        return self.get_res_dir()

    def get_res_dir(self):
        return os.path.join(self.get_base_gen_dir(), 'res', 'merged', 'debug')

    def get_assets_dir(self):
        return os.path.join(self.get_base_gen_dir(), 'assets', 'debug')

    def get_res_build_job_path(self):
        return os.path.join(self.get_base_gen_dir(), 'res.job')

    def get_base_build_dir(self):
        return os.path.join(self._module_path, 'build')

    def get_base_gen_dir(self):
        return os.path.join(self.get_base_build_dir(), 'intermediates')

    def get_dst_classes_dir(self):
        if self._config is not None and 'product_flavor' in self._config:
            if self._module_name == self._config['main_project_name']:
                if self._config['product_flavor'] == '' or self._config['product_flavor'] == 'debug':
                    return os.path.join(self.get_base_gen_dir(), 'classes', 'debug')
                else:
                    return os.path.join(self.get_base_gen_dir(), 'classes', self._config['product_flavor'], 'debug')
            else:
                release_dir = os.path.join(self.get_base_gen_dir(), 'classes', 'release')
                if not os.path.exists(release_dir):
                    release_dir = os.path.join(self.get_base_gen_dir(), 'classes', 'debug')
                return release_dir
        return GradleDirectoryFinder.find_dst_classes_dir(self.get_base_gen_dir(), package_name=self._package_name)

    @staticmethod
    def find_dst_classes_dir(base_dir, package_name=None):
        if not package_name:
            return base_dir
        pdn = package_name.replace('.', os.sep)
        for dirpath, dirs, files in os.walk(base_dir):
            if pdn in dirpath:
                return dirpath.split(pdn)[0]
        return base_dir


class GradleMergeDexTask(android_tools.MergeDexTask):
    def __init__(self, cache_dir, all_modules, project_info):
        android_tools.MergeDexTask.__init__(self, cache_dir, all_modules)
        self._all_modules = [project_info[module]['name'] for module in self._all_modules]


class GradleSyncTask(android_tools.SyncTask):
    def __init__(self, client, cache_dir):
        android_tools.SyncTask.__init__(self, client, 'gradle_sync_task')
        self._client = client
        self._is_need_restart = android_tools.is_need_restart(cache_dir)

    def execute(self):
        try:
            # self._client.push_full_res_pack()
            self._client.sync_incremental_res()
            self._client.sync_incremental_dex()
            self._client.sync_incremental_native()
            self._client.sync_state(self._is_need_restart)
            self._client.close_connection()
        except FreelineException as e:
            raise e
        except Exception:
            import traceback
            raise FreelineException('sync files to your device failed', traceback.format_exc())


class GradleSyncClient(SyncClient):
    def __init__(self, is_art, config, project_info, all_modules):
        SyncClient.__init__(self, is_art, config)
        self._project_info = project_info
        self._all_modules = all_modules
        self._is_need_sync_base_res = False

    def check_base_res_exist(self):
        url = 'http://127.0.0.1:{}/checkResource'.format(self._port)
        self.debug('checkresource: ' + url)
        result, err, code = curl(url)
        if code != 0:
            raise FreelineException('check base res failed', err.message)
        if int(result) == 0:
            self.debug('base resource not exists, need to sync full resource pack first')
            self._is_need_sync_base_res = True
        else:
            self.debug('base resource exists, there is no need to sync full resource pack')

    def push_full_res_pack(self):
        if self._is_need_sync_base_res:
            full_pack_path = get_base_resource_path(self._cache_dir)
            if os.path.exists(full_pack_path):
                self.debug('start to sync full resource pack...')
                self.debug('full pack size: {}kb'.format(os.path.getsize(full_pack_path) / 1000))
                with open(full_pack_path, 'rb') as fp:
                    url = 'http://127.0.0.1:{}/pushFullResourcePack'.format(self._port)
                    self.debug('pushfullpack: ' + url)
                    result, err, code = curl(url, body=fp.read())
                    if code != 0:
                        raise FreelineException('push full res pack failed', err.message)
            else:
                raise FreelineException('You may need a clean build.',
                                        'full resource pack not found: {}'.format(full_pack_path))

    def sync_incremental_native(self):
        if self._is_need_sync_native():
            self.debug('start to sync native file...')
            native_zip_path = get_sync_native_file_path(self._config['build_cache_dir'])
            with open(native_zip_path, "rb") as fp:
                url = "http://127.0.0.1:{}/pushNative?restart".format(self._port)
                self.debug("pushNative: "+url)
                result, err, code = curl(url, body=fp.read())
                self.debug("code: {}".format(code))
                # todo 此处返回-1 暂时先忽略
                # if code != 0:
                #     from exceptions import FreelineException
                #     raise FreelineException("sync native dex failed.",err.message)

    def sync_incremental_res(self):
        mode = 'increment' if self._is_art else 'full'
        can_sync_inc_res = False
        for module in self._all_modules:
            finder = GradleDirectoryFinder(module, self._project_info[module]['path'], self._cache_dir)
            fpath = finder.get_dst_res_pack_path(module)
            sync_status = finder.get_sync_file_path()

            if not os.path.exists(sync_status):
                self.debug('{} has no need to sync inc res pack.'.format(module))
                continue

            if not can_sync_inc_res and self._is_art:
                self.check_base_res_exist()
                self.push_full_res_pack()
                can_sync_inc_res = True

            self.debug('start to sync {} incremental res pack...'.format(module))
            self.debug('{} pack size: {}kb'.format(module, os.path.getsize(fpath) / 1000))
            with open(fpath, 'rb') as fp:
                url = 'http://127.0.0.1:{}/pushResource?mode={}&bundleId={}'.format(self._port, mode, 'base-res')
                self.debug('pushres: ' + url)
                result, err, code = curl(url, body=fp.read())
                if code != 0:
                    raise FreelineException('sync incremental respack failed', err.message)

                android_tools.clean_res_build_job_flag(finder.get_res_build_job_path())
                self.debug('sync {} incremental res pack finished'.format(module))

    def _get_apktime_path(self):
        return android_tools.get_apktime_path(self._config)

    def _is_need_sync_res(self):
        for module in self._all_modules:
            finder = GradleDirectoryFinder(module, self._project_info[module]['path'], self._cache_dir)
            fpath = finder.get_dst_res_pack_path(module)
            sync_status = fpath.replace('.pack', '.sync')
            if os.path.exists(sync_status):
                return True
        return False

    def _is_need_sync_native(self):
        return os.path.exists(os.path.join(self._config['build_cache_dir'], 'natives.zip'))


class GradleCleanCacheTask(android_tools.CleanCacheTask):
    def __init__(self, cache_dir, project_info):
        android_tools.CleanCacheTask.__init__(self, cache_dir, project_info)

    def execute(self):
        android_tools.clean_src_changed_flag(self._cache_dir)
        android_tools.clean_res_changed_flag(self._cache_dir)
        for dirpath, dirnames, files in os.walk(self._cache_dir):
            for fn in files:
                if fn.endswith('.sync'):
                    os.remove(os.path.join(dirpath, fn))
                    module = fn[:fn.rfind('.')]
                    self._refresh_public_files(module)

                if fn.endswith('increment.dex') or fn.endswith('.rflag') or fn.endswith('.restart') or fn.endswith(
                        'natives.zip'):
                    fpath = os.path.join(dirpath, fn)
                    self.debug("remove cache: {}".format(fpath))
                    os.remove(fpath)

    def _refresh_public_files(self, module):
        finder = GradleDirectoryFinder(module, self._project_info[module]['path'], self._cache_dir,
                                       package_name=self._project_info[module]['packagename'])
        public_xml_path = finder.get_public_xml_path()
        ids_xml_path = finder.get_ids_xml_path()
        rpath = finder.get_backup_r_path()
        if os.path.exists(public_xml_path) and os.path.exists(ids_xml_path) and os.path.exists(rpath):
            # self.debug('refresh {} public.xml and ids.xml'.format(dirpath))
            # android_tools.generate_public_files_by_r(rpath, public_xml_path, ids_xml_path)
            # android_tools.merge_public_file_with_old(public_xml_path, ids_xml_path, dirpath)
            self.debug('refresh {} ids.xml'.format(module))
            android_tools.generate_id_file_by_public(public_xml_path, ids_xml_path)


class BuildBaseResourceTask(Task):
    def __init__(self, config, project_info):
        Task.__init__(self, 'build_base_resource_task')
        self.__init_attributes()

    def __init_attributes(self):
        # refresh config and project info
        from dispatcher import read_freeline_config
        self._config = read_freeline_config()
        self._project_info = get_project_info(self._config)

        # self._main_dir = self._config['main_project_dir']
        self._main_module_name = self._config['main_project_name']
        self._module_info = self._project_info[self._main_module_name]
        self._finder = GradleDirectoryFinder(self._main_module_name, self._project_info[self._main_module_name]['path'],
                                             self._config['build_cache_dir'], config=self._config,
                                             package_name=self._module_info['packagename'])
        self._public_xml_path = self._finder.get_public_xml_path()
        self._ids_xml_path = self._finder.get_ids_xml_path()

    def execute(self):
        self.__init_attributes()
        self.keep_ids()
        from tracing import Tracing
        with Tracing('build_base_resource_aapt_task'):
            self.run_aapt()

    def keep_ids(self):
        public_keep_path = os.path.join(self._config['build_cache_dir'], 'public_keeper.xml')
        if os.path.exists(public_keep_path):
            self.debug('{} exists, move to dst: {}'.format(public_keep_path, self._public_xml_path))
            import shutil
            shutil.copy(public_keep_path, self._public_xml_path)
            self.debug('generating ids.xml from public.xml...')
            android_tools.generate_id_file_by_public(self._public_xml_path, self._ids_xml_path)
        else:
            self.debug('generating public.xml and ids.xml...')
            rpath = self._finder.get_dst_r_path(config=self._config)
            self.debug('origin R.java path: ' + rpath)
            android_tools.generate_public_files_by_r(rpath, self._public_xml_path, self._ids_xml_path)

    def run_aapt(self):
        aapt_args = [Builder.get_aapt(), 'package', '-f', '-I',
                     os.path.join(self._config['compile_sdk_directory'], 'android.jar'),
                     '-M', fix_package_name(self._config, self._finder.get_dst_manifest_path())]

        for rdir in self._config['project_source_sets'][self._main_module_name]['main_res_directory']:
            if os.path.exists(rdir):
                aapt_args.append('-S')
                aapt_args.append(rdir)

        for rdir in self._module_info['local_dep_res_path']:
            if os.path.exists(rdir):
                aapt_args.append('-S')
                aapt_args.append(rdir)

        if 'extra_dep_res_paths' in self._config and self._config['extra_dep_res_paths'] is not None:
            arr = self._config['extra_dep_res_paths']
            for path in arr:
                path = path.strip()
                if os.path.isdir(path):
                    aapt_args.append('-S')
                    aapt_args.append(path)

        for resdir in self._module_info['dep_res_path']:
            if os.path.exists(resdir):
                aapt_args.append('-S')
                aapt_args.append(resdir)

        aapt_args.extend(['-S', self._finder.get_backup_res_dir()])

        freeline_assets_dir = os.path.join(self._config['build_cache_dir'], 'freeline-assets')
        aapt_args.append('-A')
        aapt_args.append(freeline_assets_dir)

        for adir in self._config['project_source_sets'][self._main_module_name]['main_assets_directory']:
            if os.path.exists(adir):
                aapt_args.append('-A')
                aapt_args.append(adir)

        for m in self._module_info['local_module_dep']:
            if m in self._config['project_source_sets']:
                for adir in self._config['project_source_sets'][m]['main_assets_directory']:
                    if os.path.exists(adir):
                        aapt_args.append('-A')
                        aapt_args.append(adir)

        base_resource_path = get_base_resource_path(self._config['build_cache_dir'])
        aapt_args.append('-m')
        aapt_args.append('-J')
        aapt_args.append(self._finder.get_backup_dir())
        aapt_args.append('--auto-add-overlay')
        aapt_args.append('-F')
        aapt_args.append(base_resource_path)
        aapt_args.append('--debug-mode')
        aapt_args.append('--no-version-vectors')
        aapt_args.append('--resoucres-md5-cache-path')
        aapt_args.append(os.path.join(self._config['build_cache_dir'], "arsc_cache.dat"))
        aapt_args.append('--ignore-assets')
        aapt_args.append('public_id.xml:public.xml:*.bak:.*')

        self.debug('aapt exec: ' + ' '.join(aapt_args))
        output, err, code = cexec(aapt_args, callback=None)

        if code != 0:
            raise FreelineException('build base resources failed with: {}'.format(' '.join(aapt_args)),
                                    '{}\n{}'.format(output, err))
        self.debug('generate base resource success: {}'.format(base_resource_path))


def get_base_resource_path(cache_dir):
    return os.path.join(cache_dir, 'base-res.so')


def get_sync_native_file_path(cache_dir):
    return os.path.join(cache_dir, 'natives.zip')


def get_classpath_by_src_path(module, sdir, src_path):
    if src_path:
        target_dir = os.path.join(module, 'build', 'intermediates', 'classes', 'debug')
        return src_path.replace('.java', '.class').replace(sdir, target_dir)


def fix_package_name(config, manifest):
    if config and config['package'] != config['debug_package']:
        finder = GradleDirectoryFinder(config['main_project_name'], config['main_project_dir'],
                                       config['build_cache_dir'], config=config)
        target_manifest_path = os.path.join(finder.get_backup_dir(), 'AndroidManifest.xml')
        if os.path.exists(target_manifest_path):
            return target_manifest_path

        if manifest and os.path.isfile(manifest):
            Logger.debug('find app has debug package name, freeline will fix the package name in manifest')
            content = get_file_content(manifest)
            result = re.sub('package=\"(.*)\"', 'package=\"{}\"'.format(config['package']), content)
            Logger.debug('change package name from {} to {}'.format(config['debug_package'], config['package']))
            from utils import write_file_content
            write_file_content(target_manifest_path, result)
            Logger.debug('save new manifest to {}'.format(target_manifest_path))
            return target_manifest_path
    return manifest


def get_all_modules(dir_path):
    settings_path = os.path.join(dir_path, 'settings.gradle')
    if os.path.isfile(settings_path):
        data = get_file_content(settings_path)
        modules = []
        for item in re.findall(r'''['"]:(.*?)['"]''', data):
            index = item.rfind(':')
            if index == -1:
                modules.append({'name': item, 'path': item})
            else:
                modules.append({'name': item[index + 1:], 'path': item.replace(":", os.sep)})
        # modules = [item.replace(":", os.sep) for item in re.findall(r'''['"]:(.*?)['"]''', data)]
        return filter(lambda module: os.path.isdir(os.path.join(dir_path, module['path'])), modules)
    return []


class GradleModule(object):
    def __init__(self, module_name):
        self.name = module_name
        self.path = None
        self.library_dependencies = None
        self.library_resource_dependencies = None
        self.module_dependencies = None


def get_project_info(config):
    Logger.debug("collecting project info, please wait a while...")
    project_info = {}
    if 'modules' in config:
        modules = config['modules']
    else:
        modules = get_all_modules(os.getcwd())

    jar_dependencies_path = os.path.join(config['build_cache_dir'], 'jar_dependencies.json')
    jar_dependencies = []
    if os.path.exists(jar_dependencies_path):
        jar_dependencies = load_json_cache(jar_dependencies_path)

    for module in modules:
        if module['name'] in config['project_source_sets']:
            module_info = {}
            module_info['name'] = module['name']
            module_info['path'] = module['path']
            module_info['relative_dir'] = module['path']
            module_info['dep_jar_path'] = jar_dependencies
            module_info['packagename'] = get_package_name(
                config['project_source_sets'][module['name']]['main_manifest_path'])

            if 'module_dependencies' in config:
                module_info['local_module_dep'] = config['module_dependencies'][module['name']]
            else:
                gradle_content = remove_comments(get_file_content(os.path.join(module['path'], 'build.gradle')))
                module_info['local_module_dep'] = get_local_dependency(gradle_content)

            project_info[module['name']] = module_info

    for module in modules:
        if module['name'] in config['project_source_sets']:
            if 'module_dependencies' not in config:
                local_deps = project_info[module['name']]['local_module_dep']
                for dep in project_info[module['name']]['local_module_dep']:
                    if dep in project_info:
                        local_deps.extend(project_info[dep]['local_module_dep'])
                local_deps = list(set(local_deps))
                project_info[module['name']]['local_module_dep'] = []
                for item in local_deps:
                    local_dep_name = get_module_name(item)
                    if local_dep_name in project_info:
                        project_info[module['name']]['local_module_dep'].append(local_dep_name)

            res_dependencies_path = os.path.join(config['build_cache_dir'], module['name'],
                                                 'resources_dependencies.json')
            res_dependencies = {'library_resources': [], 'local_resources': []}
            if os.path.exists(res_dependencies_path):
                res_dependencies = load_json_cache(res_dependencies_path)
            project_info[module['name']]['dep_res_path'] = res_dependencies['library_resources']

            if 'module_dependencies' not in config:
                project_info[module['name']]['local_dep_res_path'] = res_dependencies['local_resources']
            else:
                local_res_deps = []
                local_res_deps.extend(res_dependencies['local_resources'])
                deps = project_info[module['name']]['local_module_dep']
                deps = find_all_dependent_modules(deps, deps, config)
                for m in deps:
                    deppath = os.path.join(config['build_cache_dir'], m, 'resources_dependencies.json')
                    if os.path.exists(deppath):
                        dep = load_json_cache(deppath)
                        if 'local_resources' in dep:
                            local_res_deps.extend(dep['local_resources'])
                project_info[module['name']]['local_dep_res_path'] = list(set(local_res_deps))

    return project_info


def find_all_dependent_modules(arr, modules, config):
    deps = []
    for m in modules:
        deps.extend(config['module_dependencies'][m])

    if len(deps) == 0:
        return arr
    else:
        arr.extend(deps)
        return find_all_dependent_modules(arr, deps, config)


def get_module_name(module):
    if os.sep in module:
        arr = module.split(os.sep)
        return arr[len(arr) - 1]
    else:
        return module


def get_package_name(manifest):
    if manifest and os.path.isfile(manifest):
        result = re.search('package=\"(.*)\"', get_file_content(manifest))
        if result:
            return result.group(1)
    return ''


def get_local_dependency(config):
    local_dependency = []
    for m in re.finditer(r'dependencies\s*\{', config):
        depends = balanced_braces(config[m.start():])
        for localdep in re.findall(r'''[cC]ompile[\s+\(]project\(['"]:(.+?)['"]\)''', depends):
            local_dependency.append(localdep.replace(':', os.sep))
    return local_dependency


def remove_comments(data):
    # remove comments in groovy
    return re.sub(r'''(/\*([^*]|[\r\n]|(\*+([^*/]|[\r\n])))*\*+/)|(//.*)''', '', data)


def balanced_braces(arg):
    if '{' not in arg:
        return ''
    chars = []
    n = 0
    for c in arg:
        if c == '{':
            if n > 0:
                chars.append(c)
            n += 1
        elif c == '}':
            n -= 1
            if n > 0:
                chars.append(c)
            elif n == 0:
                return ''.join(chars).lstrip().rstrip()
        elif n > 0:
            chars.append(c)
    return ''
