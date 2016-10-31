# -*- coding:utf8 -*-
from __future__ import print_function

import Queue
import logging
import os
import threading
import time
import traceback

from utils import print_json, is_windows_system

FAILURE = -1
READY = 0
WAITING = 1
WORKING = 2
SUCCESS = 3


class LoggerWorker(threading.Thread):
    def __init__(self, logger, stop_event):
        threading.Thread.__init__(self)
        self.setDaemon(True)

        if not isinstance(logger, Logger):
            raise Exception('LoggerWorker should be set up with freeline.logger.Logger')

        self._logger = logger
        self._stop_event = stop_event

    def run(self):
        while not self._stop_event.isSet():
            if self._logger.debuggable:
                message = Logger.debug_messages_queue.get()
                Logger.print_debug_message(message)
            else:
                self._logger.update()
                time.sleep(self._logger.interval)

        # if in debug mode, clean the debug messages queue
        if self._logger.debuggable:
            Logger.flush_debug_messages()
        else:
            self._logger.update()


class Logger(object):
    # TODO: check screen height before log messages
    debuggable = False
    debug_messages_queue = Queue.Queue()
    temp_backup_queue = Queue.Queue()
    info_message_array = []
    TPL_DEBUG_MESSAGE = '[DEBUG] {}'

    def __init__(self, debuggable=False, interval=0.1, unit="s"):
        self.debuggable = debuggable
        self.interval = interval
        self.unit = unit

        if not is_windows_system():
            from cursor import Cursor
            from terminal import Terminal
            self.cursor = Cursor(Terminal())

        self.sorted_tasks = []

        self.tpl_running_task = '[+][{}] {} in {}{}\n'
        self.tpl_waiting_task = '[+][{}] {}\n'
        self.tpl_finished_task = '[-][{}] {} in {}{}\n'
        # self.tpl_faied_task = '[-]{}:{} in {}{}\n'
        logging.basicConfig(level=logging.DEBUG)

    def set_sorted_tasks(self, sorted_tasks):
        self.sorted_tasks = sorted_tasks

    def draw(self):
        # if len(self.sorted_tasks) > 0:
        self.cursor.restore()
        self._draw()
        self.cursor.flush()

    def clear_space(self):
        self.cursor.restore()
        self.cursor.clear_lines(self._calculate_lines_num())
        self.cursor.save()

    def update(self):
        self.clear_space()
        self.draw()

    def reset(self):
        if not self.debuggable:
            self.clear_space()
        self.sorted_tasks = []
        Logger.info_message_array = []

    @staticmethod
    def info(message):
        Logger.info_message_array.append(message)

    @staticmethod
    def debug(message):
        Logger.debug_messages_queue.put(message)
        Logger.temp_backup_queue.put(message)

    @staticmethod
    def warn(message):
        pass

    @staticmethod
    def error(message):
        pass

    @staticmethod
    def print_debug_message(message):
        if isinstance(message, dict) or isinstance(message, list):
            print_json(message)
        else:
            print(Logger.TPL_DEBUG_MESSAGE.format(message))

    @staticmethod
    def flush_debug_messages():
        while not Logger.debug_messages_queue.empty():
            message = Logger.debug_messages_queue.get()
            Logger.print_debug_message(message)

    @staticmethod
    def write_error_log(exception=None, extra=None):
        import json
        try:
            log_path = get_error_log_path()
            with open(log_path, 'w') as fp:
                while not Logger.temp_backup_queue.empty():
                    message = Logger.temp_backup_queue.get(timeout=0.5)
                    if isinstance(message, dict) or isinstance(message, list):
                        fp.write(json.dumps(message, indent=4, separators=(',', ': ')))
                    else:
                        fp.write(message)
                    fp.write('\n')

                # write extra info
                if exception:
                    fp.write(exception)
            return log_path
        except Exception as e:
            print(traceback.format_exc())
            print(e.message)
            return None

    def _calculate_lines_num(self):
        lines_count = 0
        for task in self.sorted_tasks:
            if task.can_show_log:
                lines_count += 1
        return lines_count + len(Logger.info_message_array) + 1

    def _draw(self):
        # map(lambda task: self.cursor.write(self._get_formatted_message(task)), self.sorted_tasks)
        map(lambda message: self.cursor.write(message + '\n'), Logger.info_message_array)
        map(lambda task: self.cursor.write(self._get_formatted_message(task)),
            filter(lambda task: task.can_show_log(), self.sorted_tasks))

    def _get_formatted_message(self, task):
        return {
            FAILURE: self.tpl_finished_task.format(task.name, 'failed.', task.cost_time, self.unit),
            READY: self.tpl_running_task.format(task.name, 'not start.', 'N/A', self.unit),
            WAITING: self.tpl_waiting_task.format(task.name, 'waiting...'),
            WORKING: self.tpl_running_task.format(task.name, task.running_message,
                                                  round(time.time() - task.run_start_time, 1), self.unit),
            SUCCESS: self.tpl_finished_task.format(task.name, task.finished_message, task.cost_time, self.unit)
        }.get(task.status, 'NULL')


def get_error_log_path():
    return os.path.join(get_error_log_dir(), time.strftime('%y-%m-%d %H-%M-%S') + '.log')


def get_error_log_dir():
    dir_path = os.path.join(os.path.expanduser('~'), '.freeline', 'logs')
    if not os.path.exists(dir_path):
        os.makedirs(dir_path)
    return dir_path
