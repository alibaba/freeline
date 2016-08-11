# -*- coding:utf8 -*-
from __future__ import print_function
import threading
import time
import traceback

from exceptions import FreelineException

from logger import Logger, FAILURE, READY, WAITING, WORKING, SUCCESS


class Task(object):
    def __init__(self, name):
        self.name = name
        self.parent_tasks = []
        self.child_tasks = []

        self.status = READY  # -1: failed; 0: not start; 1: waiting; 2: working; 3: success;
        self.start_time = 0
        self.run_start_time = 0
        self.cost_time = 0
        self.running_message = 'running...'
        self.finished_message = 'finished.'
        self.condition = threading.Condition()
        self.interrupted_exception = None

    def __repr__(self):
        return "[{}]".format(self.name)

    def add_parent_task(self, task):
        if task not in self.parent_tasks:
            self.parent_tasks.append(task)
            task.add_child_task(self)

    def add_child_task(self, task):
        if task not in self.child_tasks:
            self.child_tasks.append(task)
            task.add_parent_task(self)

    def is_all_parent_finished(self):
        for task in self.parent_tasks:
            if task.status != SUCCESS and task.status != FAILURE:
                return False
        return True

    def wait(self):
        self.condition.acquire()
        self.condition.wait()
        self.condition.release()

    def notify(self):
        self.condition.acquire()
        self.condition.notify()
        self.condition.release()

    def execute(self):
        raise NotImplementedError

    def can_show_log(self):
        return self.status == SUCCESS or self.status == WORKING or self.status == FAILURE

    def debug(self, message):
        Logger.debug('[{}] {}'.format(self.name, message))


class CleanBuildTask(Task):
    def __init__(self, name, config):
        Task.__init__(self, name)
        self._config = config

    def execute(self):
        raise NotImplementedError


class IncrementalBuildTask(Task):
    def __init__(self, name):
        Task.__init__(self, name)

    def execute(self):
        raise NotImplementedError


class SyncTask(Task):
    def __init__(self, client, name):
        Task.__init__(self, name)
        self._client = client

    def execute(self):
        raise NotImplementedError


class ExecutableTask(object):
    def __init__(self, task, engine):
        self.task = task
        self.engine = engine
        self._tpl_debug_message = '[{}] {}'

    def __repr__(self):
        return "<task: {}>".format(self.task.name)

    def debug(self, message):
        Logger.debug(self._tpl_debug_message.format(self.task.name, message))

    def execute(self):
        # self.debug('{} start to execute...'.format(self.task.name))
        self.task.start_time = time.time()
        self.task.status = WAITING
        while not self.task.is_all_parent_finished():
            # self.debug('{} waiting...'.format(self.task.name))
            self.task.wait()
        self.task.run_start_time = time.time()
        self.task.status = WORKING
        self.debug('{} start to run after waiting {}s'.format(self.task.name,
                                                              round(self.task.run_start_time - self.task.start_time,
                                                                    1)))
        # check if task need to interrupt before being executing
        if self.task.interrupted_exception is not None:
            self.task.status = FAILURE
            self._pass_interrupted_exception()
            return

        try:
            self.task.execute()
            self.task.status = SUCCESS
        except FreelineException as e:
            self.task.interrupted_exception = e
            self.task.status = FAILURE
        except:
            self.task.interrupted_exception = FreelineException('unexpected exception within task',
                                                                traceback.format_exc())
            self.task.status = FAILURE

        self.task.cost_time = round(time.time() - self.task.run_start_time, 1)
        self.debug('{} finish in {}s'.format(self.task.name, round(self.task.cost_time, 1)))

        # check if task need to interrupt after being executing
        if self.task.interrupted_exception is not None:
            self._pass_interrupted_exception()
            return

        for child_task in self.task.child_tasks:
            child_task.notify()

        self._check_engine_finished()

    def _pass_interrupted_exception(self):
        for child_task in self.engine.get_running_tasks():
            child_task.interrupted_exception = self.task.interrupted_exception
            child_task.notify()

        if self.engine.is_all_tasks_finished():
            self.engine.interrupt(self.task.interrupted_exception)
            self.engine.finish()

    def _check_engine_finished(self):
        if self.engine.is_all_tasks_finished():
            self.engine.finish()


def find_root_tasks(task_list):
    return filter(lambda task: len(task.parent_tasks) == 0, task_list)


def find_last_tasks(task_list):
    return filter(lambda task: len(task.child_tasks) == 0, task_list)
