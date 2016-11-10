# -*- coding:utf8 -*-
from __future__ import print_function

import os

from android_tools import InstallApkTask, CleanAllCacheTask
from builder import CleanBuilder
from gradle_tools import GenerateFileStatTask, BuildBaseResourceTask, get_project_info, GenerateAptFilesStatTask
from task import CleanBuildTask, Task
from utils import cexec, load_json_cache, write_json_cache, is_windows_system
from logger import Logger


class GradleCleanBuilder(CleanBuilder):
    def __init__(self, config, task_engine, project_info=None, wait_for_debugger=False):
        CleanBuilder.__init__(self, config, task_engine, builder_name='gradle_clean_builder')
        self._root_task = None
        self._project_info = project_info
        self._wait_for_debugger = wait_for_debugger

    def check_build_environment(self):
        CleanBuilder.check_build_environment(self)
        if self._project_info is None:
            project_info_cache_path = os.path.join(self._config['build_cache_dir'], 'project_info_cache.json')
            if os.path.exists(project_info_cache_path):
                self._project_info = load_json_cache(project_info_cache_path)
            else:
                self._project_info = get_project_info(self._config)

    def find_dependencies(self):
        pass

    def generate_sorted_build_tasks(self):
        # tasks' order:
        # 1. generate file stat / check before clean build
        # 2. clean build
        # 3. install / clean cache
        # 4. build base res / generate project info cache
        build_task = GradleCleanBuildTask(self._config)
        install_task = InstallApkTask(self._adb, self._config, wait_for_debugger=self._wait_for_debugger)
        clean_all_cache_task = CleanAllCacheTask(self._config['build_cache_dir'], ignore=[
            'stat_cache.json', 'apktime', 'jar_dependencies.json', 'resources_dependencies.json', 'public_keeper.xml',
            'assets_dependencies.json', 'freeline_annotation_info.json'])
        build_base_resource_task = BuildBaseResourceTask(self._config, self._project_info)
        generate_stat_task = GenerateFileStatTask(self._config)
        append_stat_task = GenerateFileStatTask(self._config, is_append=True)
        read_project_info_task = GradleReadProjectInfoTask()
        generate_project_info_task = GradleGenerateProjectInfoTask(self._config)
        generate_apt_file_stat_task = GenerateAptFilesStatTask()

        # generate_stat_task.add_child_task(read_project_info_task)
        build_task.add_child_task(clean_all_cache_task)
        build_task.add_child_task(install_task)
        clean_all_cache_task.add_child_task(build_base_resource_task)
        clean_all_cache_task.add_child_task(generate_project_info_task)
        clean_all_cache_task.add_child_task(append_stat_task)
        clean_all_cache_task.add_child_task(generate_apt_file_stat_task)
        read_project_info_task.add_child_task(build_task)
        self._root_task = [generate_stat_task, read_project_info_task]

    def clean_build(self):
        self._task_engine.add_root_task(self._root_task)
        self._task_engine.start()


class GradleReadProjectInfoTask(Task):
    def __init__(self):
        Task.__init__(self, 'read_project_info_task')

    def execute(self):
        command = './gradlew -q checkBeforeCleanBuild'
        if is_windows_system():
            command = 'gradlew.bat -q checkBeforeCleanBuild'

        output, err, code = cexec(command.split(' '), callback=None)
        if code != 0:
            from exceptions import FreelineException
            raise FreelineException('freeline failed when read project info with script: {}'.format(command),
                                    '{}\n{}'.format(output, err))


class GradleGenerateProjectInfoTask(Task):
    def __init__(self, config):
        Task.__init__(self, 'generate_project_info_task')

    def execute(self):
        # reload project info
        from dispatcher import read_freeline_config
        config = read_freeline_config()

        write_json_cache(os.path.join(config['build_cache_dir'], 'project_info_cache.json'),
                         get_project_info(config))


class GradleCleanBuildTask(CleanBuildTask):
    def __init__(self, config):
        CleanBuildTask.__init__(self, 'gradle_clean_build_task', config)

    def execute(self):
        # reload config
        from dispatcher import read_freeline_config
        self._config = read_freeline_config()

        cwd = self._config['build_script_work_directory'].strip()
        if not cwd or not os.path.isdir(cwd):
            cwd = None

        command = self._config['build_script']
        command += ' -P freelineBuild=true'
        if 'auto_dependency' in self._config and not self._config['auto_dependency']:
            command += ' -PdisableAutoDependency=true'
        if Logger.debuggable:
            command += ' --stacktrace'
        self.debug(command)
        self.debug("Gradle build task is running, please wait a minute...")
        output, err, code = cexec(command.split(' '), callback=None, cwd=cwd)
        if code != 0:
            from exceptions import FreelineException
            raise FreelineException('build failed with script: {}'.format(command), '{}\n{}'.format(output, err))
