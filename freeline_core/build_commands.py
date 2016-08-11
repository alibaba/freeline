# -*- coding:utf8 -*-
from command import AbstractBuildCommand, MacroCommand


class CleanBuildCommand(AbstractBuildCommand):
    def __init__(self, builder):
        AbstractBuildCommand.__init__(self, builder, command_name='clean_build')
        self._setup()

    def execute(self):
        map(lambda command: command.execute(), self.command_list)

    def _setup(self):
        self.add_command(CheckBulidEnvironmentCommand(self._builder))
        self.add_command(FindDependenciesOfTasksCommand(self._builder))
        self.add_command(GenerateSortedBuildTasksCommand(self._builder))
        self.add_command(UpdateApkCreatedTimeCommand(self._builder))
        self.add_command(ExecuteCleanBuildCommand(self._builder))


class IncrementalBuildCommand(AbstractBuildCommand):
    def __init__(self, builder):
        AbstractBuildCommand.__init__(self, builder, command_name='incremental_build')
        self._setup()

    def execute(self):
        map(lambda command: command.execute(), self.command_list)

    def _setup(self):
        self.add_command(CheckBulidEnvironmentCommand(self._builder))
        self.add_command(GenerateSortedBuildTasksCommand(self._builder))
        self.add_command(ExecuteIncrementalBuildCommand(self._builder))


class CheckBulidEnvironmentCommand(AbstractBuildCommand):
    def __init__(self, builder):
        AbstractBuildCommand.__init__(self, builder, command_name='check_build_environment')

    def execute(self):
        self._builder.check_build_environment()


class FindDependenciesOfTasksCommand(AbstractBuildCommand):
    def __init__(self, builder):
        AbstractBuildCommand.__init__(self, builder, command_name='find_dependencies_of_tasks')

    def execute(self):
        self._builder.find_dependencies()


class GenerateSortedBuildTasksCommand(AbstractBuildCommand):
    def __init__(self, builder):
        AbstractBuildCommand.__init__(self, builder, command_name='generate_build_tasks')

    def execute(self):
        self._builder.generate_sorted_build_tasks()


class UpdateApkCreatedTimeCommand(AbstractBuildCommand):
    def __init__(self, builder):
        AbstractBuildCommand.__init__(self, builder, command_name='update_apk_created_time')

    def execute(self):
        self._builder.update_apk_created_time()


class ExecuteCleanBuildCommand(AbstractBuildCommand):
    def __init__(self, builder):
        AbstractBuildCommand.__init__(self, builder, command_name='execute_clean_build')

    def execute(self):
        self._builder.clean_build()


class ExecuteIncrementalBuildCommand(AbstractBuildCommand):
    def __init__(self, builder):
        AbstractBuildCommand.__init__(self, builder, command_name='execute_incremental_build')

    def execute(self):
        self._builder.incremental_build()


class CompileCommand(MacroCommand):
    def __init__(self, name, invoker):
        MacroCommand.__init__(self, name)
        self._invoker = invoker
        self._setup()

    def _setup(self):
        self.add_command(IncAaptCommand(self.command_name, self._invoker))
        self.add_command(IncJavacCommand(self.command_name, self._invoker))
        self.add_command(IncDexCommand(self.command_name, self._invoker))


class IncAaptCommand(MacroCommand):
    def __init__(self, pro, invoker):
        MacroCommand.__init__(self, '{}_inc_res_compile'.format(pro))
        self._invoker = invoker

    def execute(self):
        self._invoker.run_aapt_task()


class IncJavacCommand(MacroCommand):
    def __init__(self, pro, invoker):
        MacroCommand.__init__(self, '{}_inc_javac_compile'.format(pro))
        self._invoker = invoker

    def execute(self):
        self._invoker.run_javac_task()


class IncDexCommand(MacroCommand):
    def __init__(self, pro, invoker):
        MacroCommand.__init__(self, '{}_inc_dex_compile'.format(pro))
        self._invoker = invoker

    def execute(self):
        self._invoker.run_dex_task()


class SyncCommand(MacroCommand):
    def __init__(self, sync_client, command_name):
        MacroCommand.__init__(self, command_name=command_name)
        self._sync_client = sync_client
