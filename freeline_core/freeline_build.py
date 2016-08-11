# -*- coding:utf8 -*-
from __future__ import print_function
from command import AbstractCommand
from exceptions import NoConfigFoundException
from logger import Logger


class FreelineBuildCommand(AbstractCommand):
    def __init__(self, config, task_engine=None):
        AbstractCommand.__init__(self, command_name='freeline_build_command')
        self._config = config
        self._task_engine = task_engine
        self._project_type = None
        self._dispatch_policy = None
        self._builder = None
        self._scan_command = None
        self._build_command = None
        self._setup()

    def execute(self):
        file_changed_dict = self._scan_command.execute()

        if self._dispatch_policy.is_need_clean_build(self._config, file_changed_dict):
            self._setup_clean_builder(file_changed_dict)
            from build_commands import CleanBuildCommand
            self._build_command = CleanBuildCommand(self._builder)
        else:
            # only flush changed list when your project need a incremental build.
            Logger.debug('file changed list:')
            Logger.debug(file_changed_dict)
            self._setup_inc_builder(file_changed_dict)
            from build_commands import IncrementalBuildCommand
            self._build_command = IncrementalBuildCommand(self._builder)

        self._build_command.execute()

    def _setup(self):
        if not self._config:
            raise NoConfigFoundException

        if 'project_type' in self._config:
            self._project_type = self._config['project_type']
            if self._project_type == 'gradle':
                from gradle_tools import GradleScanChangedFilesCommand, GradleDispatchPolicy
                self._scan_command = GradleScanChangedFilesCommand(self._config)
                self._dispatch_policy = GradleDispatchPolicy()

    def _setup_clean_builder(self, file_changed_dict):
        if self._project_type == 'gradle':
            from gradle_clean_build import GradleCleanBuilder
            project_info = self._scan_command.project_info
            self._builder = GradleCleanBuilder(self._config, self._task_engine, project_info=project_info)

    def _setup_inc_builder(self, file_changed_dict):
        if self._project_type == 'gradle':
            project_info = self._scan_command.project_info
            from gradle_inc_build import GradleIncBuilder
            self._builder = GradleIncBuilder(file_changed_dict, self._config, self._task_engine,
                                             project_info=project_info)


class DispatchPolicy(object):
    """
    file_changed_dict:

     'projects': {
        bundle1: {
            'js': [],
            'assets': [],
            'res': [],
            'src': [],
            'manifest': [],
            'pom': []
        },
        bundle2: {
            'js': [],
            'assets': [],
            'res': [],
            'src': [],
            'manifest': [],
            'pom': []
        },
        ...
     },

     'build_info': {
        'last_clean_build_time': int,
        'root_pom_changed': bool
     }
    """

    def is_need_clean_build(self, config, file_changed_dict):
        raise NotImplementedError


class ScanChangedFilesCommand(AbstractCommand):
    def __init__(self):
        AbstractCommand.__init__(self, command_name='scan_changed_files_command')

    def execute(self):
        raise NotImplementedError
