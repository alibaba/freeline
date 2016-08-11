# -*- coding:utf8 -*-
from __future__ import print_function
from utils import generate_random_string
from logger import Logger


class AbstractCommand(object):
    TPL_COMMAND_DEBUG_MSG = '[{}] {}'

    def __init__(self, command_name=None):
        self.command_name = generate_random_string() if not command_name else command_name

    def execute(self):
        raise NotImplementedError

    def debug(self, message):
        Logger.debug(AbstractBuildCommand.TPL_COMMAND_DEBUG_MSG.format(self.command_name, message))

    def __repr__(self):
        return self.command_name

    __str__ = __repr__


class MacroCommand(AbstractCommand):
    def __init__(self, command_name=None):
        AbstractCommand.__init__(self, command_name=command_name)
        self.command_list = []

    def add_command(self, command):
        if isinstance(command, AbstractCommand):
            self.command_list.append(command)

    def remove_command(self, command):
        self.command_list.remove(command)

    def execute(self):
        map(lambda command: command.execute(), self.command_list)


class AbstractBuildCommand(MacroCommand):
    def __init__(self, builder, command_name=None):
        MacroCommand.__init__(self, command_name=command_name)
        self._builder = builder


class AbstractCleanBuildCommand(AbstractBuildCommand):
    def __init__(self, builder, command_name=None):
        AbstractBuildCommand.__init__(self, builder, command_name=command_name)

    def execute(self):
        raise NotImplementedError


class AbstractIncrementalBuildCommand(AbstractBuildCommand):
    def __init__(self, builder, command_name=None):
        AbstractBuildCommand.__init__(self, builder, command_name=command_name)

    def execute(self):
        raise NotImplementedError
