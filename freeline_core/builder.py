# -*- coding:utf8 -*-
import os

from logger import Logger
from utils import generate_random_string, is_exe, remove_namespace, is_windows_system, is_linux_system

try:
    import xml.etree.cElementTree as ET
except ImportError:
    import xml.etree.ElementTree as ET


class Builder(object):
    TPL_BUILDER_DEBUG_MSG = "[{}] {}"

    def __init__(self, config, task_engine, builder_name=None):
        self.builder_name = 'builder' + generate_random_string() if not builder_name else builder_name
        self._config = config
        self._task_engine = task_engine

    def debug(self, message):
        Logger.debug(Builder.TPL_BUILDER_DEBUG_MSG.format(self.builder_name, message))

    @staticmethod
    def get_android_sdk_dir(config):
        if 'sdk_directory' in config and os.path.exists(config['sdk_directory']):
            return config['sdk_directory']

        sdk_dir = os.getenv('ANDROID_HOME')
        if sdk_dir and os.path.isdir(sdk_dir):
            return sdk_dir

        sdk_dir = os.getenv('ANDROID_SDK')
        if sdk_dir and os.path.isdir(sdk_dir):
            return sdk_dir

        Logger.debug('[ERROR] config[sdk_directory]、ANDROID_HOME、ANDROID_SDK not found, '
                     'Build.get_android_sdk_dir() return None.')
        return None

    @staticmethod
    def get_adb(config):
        sdk_dir = Builder.get_android_sdk_dir(config)
        adb_exe_name = os.name == 'nt' and 'adb.exe' or 'adb'
        if os.path.isdir(sdk_dir) and is_exe(os.path.join(sdk_dir, 'platform-tools', adb_exe_name)):
            return os.path.join(sdk_dir, 'platform-tools', adb_exe_name)
        Logger.debug('[ERROR] Builder.get_adb() return None.')
        return None

    @staticmethod
    def get_maven_home_dir():
        path = os.getenv('M2_HOME')
        if not path:
            path = os.getenv('MAVEN_HOME')
        return path

    @staticmethod
    def get_mvn():
        mvn_exe_name = os.name == 'nt' and 'mvn.bat' or 'mvn'
        path = Builder.get_maven_home_dir()
        if os.path.isdir(path) and os.path.isdir(os.path.join(path, 'bin')):
            mvn_exe_path = os.path.join(path, 'bin', mvn_exe_name)
            return mvn_exe_path if is_exe(mvn_exe_path) else None

    @staticmethod
    def get_maven_cache_dir():
        path = Builder.get_maven_home_dir()
        if os.path.isdir(path):
            settings_path = os.path.join(path, 'conf', 'settings.xml')
            if os.path.exists(settings_path):
                tree = ET.ElementTree(ET.fromstring(remove_namespace(settings_path)))
                local_repo_node = tree.find('localRepository')
                if local_repo_node is not None:
                    return local_repo_node.text
        return None

    @staticmethod
    def get_aapt():
        aapt = os.path.join('freeline', 'release-tools', 'FreelineAapt')
        if is_windows_system():
            aapt = os.path.join('freeline', 'release-tools', 'FreelineAapt.exe')
        if is_linux_system():
            aapt = os.path.join('freeline', 'release-tools', 'FreelineAapt_')
        return aapt if os.path.exists(aapt) else None

    @staticmethod
    def get_javac(config=None):
        path = os.getenv('JAVA_HOME')
        if config is not None and 'java_home' in config:
            path = config['java_home']
        exec_name = 'javac.exe' if is_windows_system() else 'javac'
        if path and is_exe(os.path.join(path, 'bin', exec_name)):
            return os.path.join(path, 'bin', exec_name)
        Logger.debug('[ERROR] Builder.get_javac() return None.')
        return None

    @staticmethod
    def get_java(config=None):
        path = os.getenv('JAVA_HOME')
        if config is not None and 'java_home' in config:
            path = config['java_home']
        exec_name = 'java.exe' if is_windows_system() else 'java'
        if path and is_exe(os.path.join(path, 'bin', exec_name)):
            return os.path.join(path, 'bin', exec_name)
        Logger.debug('[ERROR] Builder.get_java() return None.')
        return None

    @staticmethod
    def get_dx(config):
        if is_windows_system():
            if 'build_tools_directory' in config and os.path.exists(config['build_tools_directory']):
                path = os.path.join(config['build_tools_directory'], 'dx.bat')
                if is_exe(path):
                    return path
        else:
            return os.path.join('freeline', 'release-tools', 'dx')

    @staticmethod
    def get_databinding_cli(config):
        dbcli = os.path.join('freeline', 'release-tools', 'databinding-cli.jar')
        if 'use_jdk8' in config:
            if config['use_jdk8']:
                dbcli = os.path.join('freeline', 'release-tools', 'databinding-cli8.jar')
        return dbcli


class CleanBuilder(Builder):
    def __init__(self, config, task_engine, builder_name=None):
        Builder.__init__(self, config, task_engine, builder_name=builder_name)
        self._adb = None
        self._is_art = False

    def check_build_environment(self):
        from android_tools import get_device_sdk_version_by_adb
        self._adb = Builder.get_adb(self._config)
        self._is_art = get_device_sdk_version_by_adb(self._adb) >= 20

    def find_dependencies(self):
        raise NotImplementedError

    def generate_sorted_build_tasks(self):
        raise NotImplementedError

    def update_apk_created_time(self):
        from android_tools import get_apktime_path
        from sync_client import update_clean_build_created_flag
        update_clean_build_created_flag(get_apktime_path(self._config))

    def clean_build(self):
        raise NotImplementedError


class IncrementalBuilder(Builder):
    def __init__(self, changed_files, config, task_engine, builder_name=None):
        Builder.__init__(self, config, task_engine, builder_name=builder_name)
        self._changed_files = changed_files

    def check_build_environment(self):
        raise NotImplementedError
