# -*- coding:utf8 -*-
import os
import re
import traceback
import shutil
import time

from logger import Logger
from builder import Builder
from task import Task, SyncTask, IncrementalBuildTask
from utils import cexec, write_file_content, get_file_content, merge_xml, get_md5, load_json_cache, is_windows_system, \
    write_json_cache, calculate_typed_file_count, remove_namespace
from command import AbstractCommand
from exceptions import FreelineException
from sync_client import SyncClient

try:
    import xml.etree.cElementTree as ET
except ImportError:
    import xml.etree.ElementTree as ET


class InstallApkTask(Task):
    def __init__(self, adb, config, wait_for_debugger=False):
        Task.__init__(self, 'install_apk_task')
        self._adb = adb
        self._wait_for_debugger = wait_for_debugger

    def __init_attributes(self):
        # reload freeline config
        from dispatcher import read_freeline_config
        self._config = read_freeline_config()
        self._apk_path = self._config['apk_path']
        self._launcher = self._config['launcher']
        self._cache_dir = self._config['build_cache_dir']
        self._package = self._config['package']
        if 'debug_package' in self._config:
            # support applicationIdSuffix attribute
            self._package = self._config['debug_package']

    def execute(self):
        self.__init_attributes()
        self._check_connection()
        self._install_apk()
        self._debug_app()
        self._launch_application()

    def _check_connection(self):
        self.debug('check device\' connection...')
        commands = [self._adb, 'devices']
        output, err, code = cexec(commands, callback=None)
        if code == 0:
            length = len(output.strip().split('\n'))
            from exceptions import UsbConnectionException
            if length < 2:
                raise UsbConnectionException('No device\'s connection found',
                                             '\tUse `adb devices` to check your device connection')
            if length > 2:
                raise UsbConnectionException('More than 1 device connect',
                                             '\tOnly 1 device allowed, '
                                             'use `adb devices` to check your devices\' connection')

    def _install_apk(self):
        if self._adb:
            if not os.path.exists(self._apk_path):
                raise FreelineException('apk not found.', 'apk path: {}, not exists.'.format(self._apk_path))

            install_args = [self._adb, 'install', '-r', self._apk_path]
            self.debug('start to install apk to device: {}'.format(' '.join(install_args)))
            output, err, code = cexec(install_args, callback=None)

            if 'Failure' in output:
                self.debug('install apk failed, start to retry.')
                output, err, code = cexec(install_args, callback=None)
                if 'Failure' in output:
                    raise FreelineException('install apk to device failed.', '{}\n{}'.format(output, err))

    def _debug_app(self):
        if self._wait_for_debugger:
            adb_args = [Builder.get_adb(self._config), 'shell', 'am', 'set-debug-app', '-w', self._package]
            self.debug('make application wait for debugger: {}'.format(' '.join(adb_args)))
            cexec(adb_args, callback=None)

    def _launch_application(self):
        if self._package and self._launcher:
            adb_args = [self._adb, 'shell', 'am', 'start', '-n', self._package + '/' + self._launcher]
            self.debug('start to launch application {}/{}'.format(self._package, self._launcher))
            self.debug(' '.join(adb_args))
            cexec(adb_args, callback=None)


class ConnectDeviceTask(SyncTask):
    def __init__(self, client):
        SyncTask.__init__(self, client, 'connect_device_task')

    def execute(self):
        from exceptions import CheckSyncStateException
        try:
            self._client.connect_device()
        except CheckSyncStateException as e:
            raise e


class AndroidSyncTask(SyncTask):
    def __init__(self, client, cache_dir):
        SyncTask.__init__(self, client, 'android_sync_task')
        self._client = client
        self._is_need_restart = is_need_restart(cache_dir)

    def execute(self):
        try:
            self._client.sync_incremental_res()
            self._client.sync_incremental_dex()
            self._client.sync_state(self._is_need_restart)
            self._client.close_connection()
        except FreelineException as e:
            raise e
        except Exception:
            raise FreelineException('sync files to your device failed', traceback.format_exc())


class AndroidSyncClient(SyncClient):
    def __init__(self, is_art, config):
        SyncClient.__init__(self, is_art, config)

    def sync_incremental_native(self):
        pass

    def sync_incremental_res(self):
        pass

    def _get_apktime_path(self):
        pass

    def _is_need_sync_res(self):
        pass

    def _is_need_sync_native(self):
        pass


class CleanAllCacheTask(Task):
    def __init__(self, cache_dir, ignore=None):
        Task.__init__(self, 'clean_all_cache_task')
        self._cache_dir = cache_dir
        self._ignore = ignore

    def execute(self):
        for dirpath, dirnames, files in os.walk(self._cache_dir):
            for fn in files:
                self.__remove(dirpath, fn)

    def __remove(self, dirpath, fn):
        if self._ignore is not None:
            if fn not in self._ignore:
                os.remove(os.path.join(dirpath, fn))
            else:
                self.debug('ignore remove: {}'.format(os.path.join(dirpath, fn)))
        else:
            os.remove(os.path.join(dirpath, fn))


class UpdateStatTask(Task):
    def __init__(self, config, changed_files):
        Task.__init__(self, 'update_stat_task')
        self._config = config
        self._changed_files = changed_files

    def execute(self):
        cache_path = os.path.join(self._config['build_cache_dir'], 'stat_cache.json')
        stat_cache = load_json_cache(cache_path)
        cache_path_md5 = os.path.join(self._config['build_cache_dir'], 'stat_cache_md5.json')
        stat_cache_md5 = load_json_cache(cache_path_md5)

        for module, file_dict in self._changed_files.iteritems():
            for key, files in file_dict.iteritems():
                if key != 'apt':
                    for fpath in files:
                        if not fpath.startswith(self._config['build_cache_dir']) and os.path.exists(fpath):
                            self.debug('refresh {} stat'.format(fpath))
                            os.utime(fpath, None)
                            if fpath not in stat_cache[module]:
                                stat_cache[module][fpath] = {}

                            if fpath in stat_cache_md5:
                                stat_cache_md5[fpath] = get_md5(fpath)

                            stat_cache[module][fpath]['mtime'] = os.path.getmtime(fpath)
                            stat_cache[module][fpath]['size'] = os.path.getsize(fpath)

        write_json_cache(cache_path, stat_cache)
        write_json_cache(cache_path_md5, stat_cache_md5)


class DirectoryFinder(object):
    def __init__(self, module_name, cache_dir):
        self._module_name = module_name
        self._cache_dir = cache_dir

    def get_res_dir(self):
        raise NotImplementedError

    def get_assets_dir(self):
        raise NotImplementedError

    def get_dst_res_dir(self):
        raise NotImplementedError

    def get_dst_r_dir(self):
        raise NotImplementedError

    def get_dst_r_path(self):
        raise NotImplementedError

    def get_dst_manifest_path(self):
        raise NotImplementedError

    def get_res_build_job_path(self):
        raise NotImplementedError

    def get_backup_dir(self):
        backup_dir = os.path.join(self._cache_dir, self._module_name, 'backup')
        if not os.path.isdir(backup_dir):
            os.makedirs(backup_dir)
        return backup_dir

    def get_backup_res_dir(self):
        dir_path = os.path.join(self.get_backup_dir(), 'res')
        if not os.path.isdir(dir_path):
            os.makedirs(dir_path)
        return dir_path

    def get_backup_values_dir(self):
        backup_values_dir = os.path.join(self.get_backup_res_dir(), 'values')
        if not os.path.isdir(backup_values_dir):
            os.makedirs(backup_values_dir)
        return backup_values_dir

    def get_public_xml_path(self):
        return os.path.join(self.get_backup_values_dir(), 'freeline_id_keeper_public.xml')

    def get_ids_xml_path(self):
        return os.path.join(self.get_backup_values_dir(), 'freeline_id_keeper_ids.xml')

    def get_sync_file_path(self):
        dir_path = os.path.join(self._cache_dir, self._module_name, 'respack')
        if not os.path.isdir(dir_path):
            os.makedirs(dir_path)
        return os.path.join(dir_path, self._module_name + '.sync')

    def get_dst_dex_path(self):
        return os.path.join(self.get_patch_dex_dir(), self._module_name + '.dex')

    def get_dst_res_pack_path(self, module):
        pack_dir = os.path.join(self._cache_dir, module, 'respack')
        if not os.path.exists(pack_dir):
            os.makedirs(pack_dir)
        return os.path.join(pack_dir, module + '.pack')

    def get_patch_dex_dir(self):
        dir_path = os.path.join(self._cache_dir, self._module_name, 'dex')
        if not os.path.isdir(dir_path):
            os.makedirs(dir_path)
        return dir_path

    def get_patch_classes_cache_dir(self):
        cache_dir = os.path.join(self._cache_dir, self._module_name, 'classes')
        if not os.path.exists(cache_dir):
            os.makedirs(cache_dir)
        return cache_dir

    def get_module_cache_dir(self):
        cache_dir = os.path.join(self._cache_dir, self._module_name)
        if not os.path.exists(cache_dir):
            os.makedirs(cache_dir)
        return cache_dir

    @staticmethod
    def get_r_file_path(target_dir):
        for dirpath, dirnames, files in os.walk(target_dir):
            for fn in files:
                if fn.endswith("R.java"):
                    return os.path.join(dirpath, fn)
        return None


class QuickScanCommand(AbstractCommand):
    def __init__(self):
        AbstractCommand.__init__(self, 'quick_scan_command')

    def execute(self):
        raise NotImplementedError


class MergeDexTask(Task):
    def __init__(self, cache_dir, all_modules):
        Task.__init__(self, 'merge_dex_task')
        self._cache_dir = cache_dir
        self._all_modules = all_modules

    def execute(self):
        if is_src_changed(self._cache_dir):
            pending_merge_dexes = self._get_dexes()
            dex_path = get_incremental_dex_path(self._cache_dir)
            if len(pending_merge_dexes) == 1:
                self.debug('just 1 dex need to sync, copy {} to {}'.format(pending_merge_dexes[0], dex_path))
                shutil.copy(pending_merge_dexes[0], dex_path)
            elif len(pending_merge_dexes) > 1:
                dex_path = get_incremental_dex_path(self._cache_dir)
                dex_merge_args = ['java', '-jar', os.path.join('freeline', 'release-tools', 'DexMerge.jar'), dex_path]
                dex_merge_args.extend(pending_merge_dexes)
                self.debug('merge dex exec: ' + ' '.join(dex_merge_args))
                output, err, code = cexec(dex_merge_args, callback=None)
                if code != 0:
                    raise FreelineException('merge dex failed: {}'.format(' '.join(dex_merge_args)),
                                            output + '\n' + err)

    def _get_dexes(self):
        pending_merge_dexes = []
        target_dir = get_incremental_dex_dir(self._cache_dir)
        for module in self._all_modules:
            dir_path = os.path.join(self._cache_dir, module, 'dex')
            if os.path.isdir(dir_path):
                files = os.listdir(dir_path)
                dexes = [os.path.join(dir_path, fn) for fn in files if fn.endswith('.dex')]
                if len(dexes) == 1:
                    pending_merge_dexes.extend(dexes)
                else:
                    for dex in dexes:
                        if dex.endswith('classes.dex'):
                            shutil.copy(dex, os.path.join(target_dir, module + '-classes.dex'))
                        else:
                            pending_merge_dexes.append(dex)
        return pending_merge_dexes


class AndroidIncrementalBuildTask(IncrementalBuildTask):
    def __init__(self, name, command):
        IncrementalBuildTask.__init__(self, name)
        self._command = command

    def execute(self):
        try:
            self._command.execute()
        except FreelineException as e:
            raise e
        except Exception:
            raise FreelineException('incremental build task failed.', traceback.format_exc())


class AndroidIncBuildInvoker(object):
    def __init__(self, name, path, config, changed_files, module_info, is_art=False,
                 is_other_modules_has_src_changed=False):
        self._name = name
        self._module_path = path
        self._config = config
        self._changed_files = changed_files
        self._module_info = module_info
        self._is_art = is_art
        self._is_other_modules_has_src_changed = is_other_modules_has_src_changed

        self._aapt = Builder.get_aapt()
        self._javac = Builder.get_javac(config=config)
        if self._javac is None:
            raise FreelineException('Please declares your JAVA_HOME to system env!', 'JAVA_HOME not found in env.')
        self._dx = Builder.get_dx(self._config)
        self._cache_dir = self._config['build_cache_dir']
        self._finder = None
        self._res_dependencies = []
        self._is_ids_changed = False
        self._public_xml_path = None
        self._ids_xml_path = None
        self._new_res_list = []
        self._merged_xml_cache = {}
        self._origin_res_list = list(self._changed_files['res'])
        self._classpaths = []
        self._is_r_file_changed = False
        self._is_need_javac = True
        self._extra_javac_args = []

        self.before_execute()

    def debug(self, message):
        Logger.debug('[{}_inc_invoker] {}'.format(self._name, message))

    def before_execute(self):
        raise NotImplementedError

    def check_res_task(self):
        job_path = self._finder.get_res_build_job_path()
        if len(self._changed_files['assets']) > 0 or len(self._changed_files['res']) > 0:
            self.debug('find {} has resource files modification.'.format(self._name))
            mark_res_build_job(job_path)
        if not os.path.exists(job_path):
            return False

        if len(self._changed_files['assets']) == 0 and len(self._changed_files['res']) == 0:
            if os.path.exists(self._finder.get_sync_file_path()):
                mark_r_changed_flag(self._name, self._cache_dir)
                self.debug('{} has sync flag, skip aapt task.'.format(self._name))
                return False

        return True

    def fill_dependant_jars(self):
        raise NotImplementedError

    def check_ids_change(self):
        for fn in self._changed_files['res']:
            if 'ids.xml' in fn or 'public.xml' in fn:
                self._changed_files['res'].remove(fn)
                self._is_ids_changed = True
                self.debug('find id file {} changed.'.format(fn))

    def generate_r_file(self):
        # ${cache_dir}/${module}/backup/res/values/public.xml
        self._public_xml_path = self._finder.get_public_xml_path()
        # ${cache_dir}/${module}/backup/res/values/ids.xml
        self._ids_xml_path = self._finder.get_ids_xml_path()

        if not os.path.exists(self._public_xml_path) or not os.path.exists(self._ids_xml_path):
            # generate public.xml and ids.xml by build/target/generated-sources/r/R.java
            self.debug('generating public.xml and ids.xml...')
            generate_public_files_by_r(self._finder.get_dst_r_path(config=self._config), self._public_xml_path,
                                       self._ids_xml_path)

        # if has public.xml or ids.xml changed, merge them with the new.
        if self._is_ids_changed:
            merge_public_file_with_old(self._public_xml_path, self._ids_xml_path, self._module_info['name'],
                                       self._config)

        # self._changed_files['res'].append(self._public_xml_path)
        # self._changed_files['res'].append(self._ids_xml_path)

    def backup_res_files(self):
        pending_remove = []
        for fpath in self._changed_files['res']:
            # res/values/colors.xml -> build/target/generated-sources/res/values/colors.xml
            # res/values/colors.xml -> build/intermediates/res/merged/debug/values/colors.xml
            dst_path = self._get_res_incremental_dst_path(fpath)
            is_new_file = False
            if not os.path.exists(dst_path):
                is_new_file = True
                self._new_res_list.append(dst_path)

            if fpath in self._merged_xml_cache:
                backup_res_file(dst_path)  # backup old file
                cache = self._merged_xml_cache[fpath]
                write_file_content(dst_path, cache)  # write merged cache to dst path
            else:
                if is_new_file:
                    shutil.copyfile(fpath, dst_path)  # just copy to dst path, if this is new file
                    self.debug('copy {} to {}'.format(fpath, dst_path))
                    continue

                old_file_md5 = get_md5(fpath)
                dst_file_md5 = get_md5(dst_path)
                if old_file_md5 != dst_file_md5:
                    backup_res_file(dst_path)
                    shutil.copyfile(fpath, dst_path)
                    self.debug('copy {} to {}'.format(fpath, dst_path))
                else:
                    pending_remove.append(fpath)  # file is not changed, so remove from changed list
                    os.utime(dst_path, None)

        for fpath in self._changed_files['assets']:
            dst_path = self._get_res_incremental_dst_path(fpath)
            if os.path.exists(dst_path):
                backup_res_file(dst_path)
            else:
                self._new_res_list.append(dst_path)
            shutil.copyfile(fpath, dst_path)

        for fpath in pending_remove:
            if fpath in self._changed_files['res']:
                self._changed_files['res'].remove(fpath)

    def _get_aapt_args(self):
        raise NotImplementedError

    def run_aapt_task(self):
        self._changed_files['res'].append(self._public_xml_path)
        self._changed_files['res'].append(self._ids_xml_path)

        aapt_args, final_changed_list = self._get_aapt_args()
        self.debug('aapt exec: ' + ' '.join(aapt_args))
        st = time.time()
        output, err, code = cexec(aapt_args, callback=None)

        if code == 0:
            self.debug('aapt use time: {}ms'.format((time.time() - st) * 1000))
            self.debug('merged_changed_list:')
            self.debug(final_changed_list)
            self._backup_res_changed_list(final_changed_list)
            self._handle_with_backup_files(True)
            mark_res_sync_status(self._finder.get_sync_file_path())
        else:
            clean_res_build_job_flag(self._finder.get_res_build_job_path())
            self._handle_with_backup_files(False)
            rollback_backup_files(self._origin_res_list, self._new_res_list)
            raise FreelineException('incremental res build failed.', '{}\n{}'.format(output, err))

    def check_r_md5(self):
        old_md5 = None
        old_r_file = self._finder.get_dst_r_path(config=self._config)
        self.debug("{} old R.java path: {}".format(self._name, old_r_file))
        new_r_file = DirectoryFinder.get_r_file_path(self._finder.get_backup_dir())
        self.debug("{} new R.java path: {}".format(self._name, new_r_file))
        if old_r_file and os.path.exists(old_r_file):
            old_md5 = get_md5(old_r_file)
        if new_r_file and os.path.exists(new_r_file):
            new_md5 = get_md5(new_r_file)
            if not old_md5:
                mark_r_changed_flag(self._name, self._cache_dir)
                AndroidIncBuildInvoker.fix_for_windows(new_r_file)
                self._changed_files['src'].append(new_r_file)
                self.debug('find R.java changed (origin R.java not exists)')
            else:
                if new_md5 != old_md5:
                    mark_r_changed_flag(self._name, self._cache_dir)
                    AndroidIncBuildInvoker.fix_for_windows(new_r_file)
                    self._changed_files['src'].append(new_r_file)
                    self.debug('find R.java changed (md5 value is different from origin R.java)')

    @staticmethod
    def fix_for_windows(path):
        if is_windows_system():
            buf = fix_unicode_parse_error(get_file_content(path), path)
            write_file_content(path, buf)

    def check_javac_task(self):
        changed_count = len(self._changed_files['src'])
        apt_changed_count = 0
        if 'apt' in self._changed_files:
            apt_changed_count = len(self._changed_files['apt'])
            changed_count += apt_changed_count
            if apt_changed_count > 0:
                self.debug('apt changed files:')
                self.debug(self._changed_files['apt'])
        self.debug("src changed files:")
        self.debug(self._changed_files['src'])

        # mark is there has R.java modification in src list
        # for fpath in self._changed_files['src']:
        #     if 'R.java' in fpath:
        #         self._is_r_file_changed = True
        #         self.debug('find R.java modified in src list')
        #         break

        if changed_count == 0:
            self.debug('{} project has no change, need not go ahead'.format(self._name))
            self._is_need_javac = False

        if self._is_only_r_changed():
            if self._is_other_modules_has_src_changed:
                self.debug(
                    '{} only find R.java changed, but other modules has src files changed, so need javac task'.format(
                        self._name))
                self._is_need_javac = True
            elif apt_changed_count != 0:
                self.debug('{} has apt files changed so that it need javac task.'.format(self._name))
                self._is_need_javac = True
            else:
                self.debug('{} code only change R.java, need not go ahead'.format(self._name))
                self._is_need_javac = False

        return self._is_need_javac

    def _is_only_r_changed(self):
        is_only_r_changed = True
        for fpath in self._changed_files['src']:
            if os.sep + 'R.java' not in fpath:
                is_only_r_changed = False
            else:
                self._is_r_file_changed = True
                self.debug('find R.java modified in src list')
        return is_only_r_changed

    def fill_classpaths(self):
        raise NotImplementedError

    def clean_dex_cache(self):
        dex_path = self._finder.get_dst_dex_path()
        if os.path.isfile(dex_path):
            os.remove(dex_path)

    def run_javac_task(self):
        javacargs = [self._javac, '-target', '1.7', '-source', '1.7', '-encoding', 'UTF-8', '-g', '-cp',
                     os.pathsep.join(self._classpaths)]
        for fpath in self._changed_files['src']:
            javacargs.append(fpath)

        javacargs.extend(self._extra_javac_args)
        javacargs.append('-d')
        javacargs.append(self._finder.get_patch_classes_cache_dir())

        self.debug('javac exec: ' + ' '.join(javacargs))
        output, err, code = cexec(javacargs, callback=None)

        if code != 0:
            raise FreelineException('incremental javac compile failed.', '{}\n{}'.format(output, err))
        else:
            if self._is_r_file_changed:
                old_r_file = self._finder.get_dst_r_path(config=self._config)
                new_r_file = DirectoryFinder.get_r_file_path(self._finder.get_backup_dir())
                shutil.copyfile(new_r_file, old_r_file)
                self.debug('copy {} to {}'.format(new_r_file, old_r_file))

    def check_dex_task(self):
        patch_classes_count = calculate_typed_file_count(self._finder.get_patch_classes_cache_dir(), '.class')
        if self._is_need_javac:
            return False if patch_classes_count == 0 else True
        return False

    def run_dex_task(self):
        patch_classes_cache_dir = self._finder.get_patch_classes_cache_dir()
        # dex_path = self._finder.get_dst_dex_path()
        dex_path = self._finder.get_patch_dex_dir()
        add_path = None
        if is_windows_system():
            add_path = str(os.path.abspath(os.path.join(self._javac, os.pardir)))
            dex_args = [self._dx, '--dex', '--multi-dex', '--output=' + dex_path, patch_classes_cache_dir]
        else:
            dex_args = [self._dx, '--dex', '--no-optimize', '--force-jumbo', '--multi-dex', '--output=' + dex_path,
                        patch_classes_cache_dir]

        self.debug('dex exec: ' + ' '.join(dex_args))
        output, err, code = cexec(dex_args, add_path=add_path)

        if code != 0:
            raise FreelineException('incremental dex compile failed.', '{}\n{}'.format(output, err))
        else:
            mark_restart_flag(self._cache_dir)

    def _handle_with_backup_files(self, is_success):
        res_dir = self._finder.get_dst_res_dir()
        dst_manifest_bak = self._finder.get_dst_manifest_path() + '.bak'
        if os.path.exists(dst_manifest_bak):
            handle_with_backup_file(dst_manifest_bak, is_success)
        for dirpath, dirnames, files in os.walk(res_dir):
            for fn in files:
                if fn.endswith('.bak'):
                    fpath = os.path.join(dirpath, fn)
                    handle_with_backup_file(fpath, is_success)

    def _get_backup_res_changed_list(self):
        respack_dir = self._finder.get_dst_res_pack_path(self._name)
        cache = load_json_cache(os.path.join(respack_dir, 'rchangelist.bak'))
        changed_list = cache.get('changed_list')
        if not changed_list:
            changed_list = []
        return changed_list

    def _backup_res_changed_list(self, changed_list):
        respack_dir = self._finder.get_dst_res_pack_path(self._name)
        all_changed_list = self._get_backup_res_changed_list()
        for f in changed_list:
            if f not in all_changed_list:
                all_changed_list.append(f)
        cache = {"changed_list": all_changed_list}
        write_json_cache(os.path.join(respack_dir, 'rchangelist.bak'), cache)

    def _get_res_incremental_dst_path(self, fpath):
        raise NotImplementedError

    def _parse_changed_list(self):
        raise NotImplementedError


class CleanCacheTask(Task):
    def __init__(self, cache_dir, project_info):
        Task.__init__(self, 'clean_cache_task')
        self._cache_dir = cache_dir
        self._project_info = project_info

    def execute(self):
        clean_src_changed_flag(self._cache_dir)
        for dirpath, dirnames, files in os.walk(self._cache_dir):
            for fn in files:
                if fn.endswith('.sync'):
                    os.remove(os.path.join(dirpath, fn))
                    pro = fn.split('.')[0]
                    # refresh ids.xml and public.xml
                    if is_r_changed_flag_exiests(pro, self._cache_dir):
                        self.debug('find R.java has modification, refresh ids.xml and public.xml')
                        finder = DirectoryFinder(pro, self._cache_dir)
                        public_xml_path = finder.get_public_xml_path()
                        ids_xml_path = finder.get_ids_xml_path()
                        generate_public_files_by_r(
                            DirectoryFinder.get_r_file_path(finder.get_dst_r_dir()), public_xml_path, ids_xml_path)
                        # merge_public_file_with_old(public_xml_path, ids_xml_path,
                        #                            self._project_info[pro]['children_bundle_path'])

                if fn.endswith('increment.dex') or fn.endswith('.rflag') or fn.endswith('.restart') or fn.endswith(
                        'natives.zip'):
                    os.remove(os.path.join(dirpath, fn))


def find_r_file(target_dir, package_name=None):
    if package_name is not None:
        package_name = os.path.join(package_name.replace('.', os.sep), 'R.java')
    for dirpath, dirnames, files in os.walk(target_dir):
        for fn in files:
            if fn.endswith("R.java"):
                path = os.path.join(dirpath, fn)
                if not package_name:
                    return path
                else:
                    if package_name in path:
                        return path
    return None


def find_manifest(target_dir):
    for dirpath, dirnames, files in os.walk(target_dir):
        for fn in files:
            if fn == 'AndroidManifest.xml':
                return os.path.join(dirpath, fn)
    return None


def merge_public_file_with_old(public_xml_path, ids_xml_path, module, config):
    # rdir = get_res_dir(dirname)
    res_dirs = config['project_source_sets'][module]['main_res_directory']
    for rdir in res_dirs:
        old_public = get_file_content(os.path.join(rdir, 'values', 'public.xml'))
        write_merge_result(public_xml_path, old_public)

        old_ids = get_file_content(os.path.join(rdir, 'values', 'ids.xml'))
        write_merge_result(ids_xml_path, old_ids)


def write_merge_result(path, content):
    if len(content) > 0:
        tmp_path = path + '.temp'
        write_file_content(tmp_path, content)
        result = merge_xml([path, tmp_path])
        write_file_content(path, result)
        os.remove(tmp_path)


def get_manifest_path(dir_path):
    manifest_path = os.path.join(dir_path, 'AndroidManifest.xml')
    if os.path.isfile(manifest_path):
        return manifest_path
    manifest_path = os.path.join(dir_path, 'src', 'main', 'AndroidManifest.xml')
    return manifest_path if os.path.isfile(manifest_path) else None


def fix_unicode_parse_error(content, path):
    if content is not None and is_windows_system():
        Logger.debug("avoid windows unicode error for {}".format(path))
        return content.replace(r"\u", r"d")
    return content


def is_res_sub_dir(dir_name):
    prefixes = ['drawable', 'layout', 'values', 'anim', 'color', 'menu', 'raw', 'xml', 'mipmap', 'animator',
                'interpolator', 'transition']
    for pre in prefixes:
        if dir_name.startswith(pre):
            return True
    return False


def get_incremental_dex_path(cache_dir):
    return os.path.join(get_incremental_dex_dir(cache_dir), 'merged.dex')


def get_incremental_dex_dir(cache_dir):
    dir_path = os.path.join(cache_dir, 'freeline-dexes')
    if not os.path.exists(dir_path):
        os.makedirs(dir_path)
    return dir_path


def get_device_sdk_version_by_adb(adb):
    dev_version = 0
    try:
        output = cexec([adb, 'shell', 'getprop ro.build.version.sdk'], callback=None)
        if output and len(output) > 0:
            if isinstance(output, str):
                dev_version = int(output.strip())
            elif isinstance(output, tuple):
                dev_version = int(output[0])
    except:
        pass
    return dev_version


def mark_res_build_job(job_path):
    if not os.path.exists(job_path):
        write_file_content(job_path, '')


def clean_res_build_job_flag(job_path):
    if os.path.exists(job_path):
        os.remove(job_path)


def is_r_changed_flag_exiests(pro, cache_dir):
    path = get_rflag_path(pro, cache_dir)
    return os.path.exists(path)


def mark_r_changed_flag(pro, cache_dir):
    path = get_rflag_path(pro, cache_dir)
    if not os.path.exists(path):
        write_file_content(path, '')


def clean_r_changed_flag(pro, cache_dir):
    path = get_rflag_path(pro, cache_dir)
    if os.path.exists(path):
        os.remove(path)


def get_rflag_path(pro, cache_dir):
    dirpath = os.path.join(cache_dir, pro, 'respack')
    if not os.path.isdir(dirpath):
        os.makedirs(dirpath)
    return os.path.join(dirpath, pro + '.rflag')


def mark_res_sync_status(sync_file_path):
    if not os.path.exists(sync_file_path):
        write_file_content(sync_file_path, '')


def clean_res_sync_status(sync_file_path):
    if os.path.exists(sync_file_path):
        os.remove(sync_file_path)


def mark_restart_flag(cache_dir):
    path = os.path.join(cache_dir, 'increment.restart')
    if not os.path.exists(path):
        write_file_content(path, '')


def is_need_restart(cache_dir):
    path = os.path.join(cache_dir, 'increment.restart')
    return os.path.exists(path)


def clean_restart_flag(cache_dir):
    path = os.path.join(cache_dir, 'increment.restart')
    if os.path.exists(path):
        os.remove(path)


def mark_src_changed(cache_dir):
    path = get_src_changed_flag_path(cache_dir)
    if not os.path.exists(path):
        write_file_content(path, '')


def is_src_changed(cache_dir):
    return os.path.exists(get_src_changed_flag_path(cache_dir))


def clean_src_changed_flag(cache_dir):
    path = get_src_changed_flag_path(cache_dir)
    if os.path.exists(path):
        os.remove(path)


def get_src_changed_flag_path(cache_dir):
    return os.path.join(cache_dir, 'increment.srcflag')


def mark_res_changed(cache_dir):
    path = get_res_changed_flag_path(cache_dir)
    if not os.path.exists(path):
        write_file_content(path, '')


def is_res_changed(cache_dir):
    return os.path.exists(get_res_changed_flag_path(cache_dir))


def clean_res_changed_flag(cache_dir):
    path = get_res_changed_flag_path(cache_dir)
    if os.path.exists(path):
        os.remove(path)


def get_res_changed_flag_path(cache_dir):
    return os.path.join(cache_dir, 'increment.resflag')


def backup_res_file(fpath):
    if os.path.exists(fpath):
        backup_path = fpath + '.bak'
        Logger.debug('backup: {}'.format(fpath))
        if os.path.exists(backup_path):
            os.remove(backup_path)
        os.rename(fpath, backup_path)


def handle_with_backup_file(fpath, is_success):
    if is_success:
        os.remove(fpath)
    else:
        origin = fpath.replace('.bak', '')
        if os.path.exists(origin):
            os.remove(origin)
            os.rename(fpath, origin)


def rollback_backup_files(origin_file_list, new_file_list):
    [os.utime(fpath, None) for fpath in origin_file_list]
    [os.remove(fpath) for fpath in new_file_list]


def generate_id_file_by_public(public_path, ids_path):
    if not os.path.exists(public_path):
        raise FreelineException("public file not found", "public file path: {}".format(public_path))

    tree = ET.ElementTree(ET.fromstring(remove_namespace(public_path)))
    ids_root = ET.Element('resources')
    for elem in tree.iterfind('public[@type="id"]'):
        node = ET.SubElement(ids_root, "item")
        node.attrib['name'] = elem.attrib['name']
        node.attrib['type'] = "id"
    ids_tree = ET.ElementTree(ids_root)
    ids_tree.write(ids_path, encoding="utf-8")


def generate_public_files_by_r(dst_r_path, public_path, ids_path):
    buf = get_file_content(dst_r_path)

    temp = re.findall('<tr><td><code>([^<]+)</code></td>', buf)
    diykv = []
    for i in temp:
        if "{" not in i:
            diykv.append(i)
    dstbuf = ''
    idbuf = '<?xml version="1.0" encoding="utf-8"?>\n'
    idbuf += '<resources>\n'
    dstbuf += idbuf

    result = buf.split('\n')
    type_char = ''
    for r in result:
        if 'public static final class' in r:
            type_char = r.replace('public static final class ', '').replace(' {', '').replace(' ', '').replace('\n', '').replace('\r', '')
        elif 'public static class' in r:
            type_char = r.replace('public static class ', '').replace(' {', '').replace(' ', '').replace('\n', '').replace('\r', '')
            type_char = type_char.replace(' ', '').replace('\n', '').replace('\r', '')
        elif 'public static final int' in r and type_char != '' and '[]' not in r:
            kv = r.replace('public static final int ', '').replace(';', '').split('=')
            name = kv[0].replace(' ', '').replace('\n', '').replace('\r', '')
            id_char = kv[1].replace(' ', '').replace('\n', '').replace('\r', '')
            dstbuf += '    <public type="%s" name="%s" id="%s" />\n' % (type_char, name, id_char)
            if type_char == 'id' and name not in diykv:
                idbuf += '    <item name="%s" type="id"/>\n' % name

        elif 'public static int' in r and type_char != '' and '[]' not in r:
            kv = r.replace('public static int ', '').replace(';', '').split('=')
            name = kv[0].replace(' ', '').replace('\n', '').replace('\r', '')
            id_char = kv[1].replace(' ', '').replace('\n', '').replace('\r', '')
            dstbuf += '    <public type="%s" name="%s" id="%s" />\n' % (type_char, name, id_char)
            if type_char == 'id' and name not in diykv:
                idbuf += '    <item name="%s" type="id"/>\n' % name

        elif type_char != '' and '}' in r:
            type_char = ''

    dstbuf += '</resources>'
    idbuf += '</resources>'
    write_file_content(public_path, dstbuf)
    write_file_content(ids_path, idbuf)


def get_apktime_path(config):
    adir = os.path.join(config['build_cache_dir'], 'freeline-assets')
    if not os.path.exists(adir):
        os.makedirs(adir)
    path = os.path.join(adir, 'apktime')
    if not os.path.exists(path):
        write_file_content(path, '')
    return path


def delete_class(class_dir, class_name):
    for dirpath, dirnames, files in os.walk(class_dir):
        for fn in files:
            if fn.startswith(class_name):
                name = fn.replace('.class', '')
                if name == class_name or name.startswith(class_name + '$'):
                    Logger.debug("delete class: " + os.path.join(dirpath, fn))
                    os.remove(os.path.join(dirpath, fn))
