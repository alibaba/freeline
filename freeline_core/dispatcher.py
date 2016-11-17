# -*- coding:utf8 -*-
from __future__ import print_function
import os
import traceback
import threading
import time

from command import AbstractCommand
from exceptions import NoConfigFoundException, CheckSyncStateException, FreelineException, NoInstallationException, \
    FileMissedException
from logger import Logger, LoggerWorker
from task_engine import TaskEngine
from utils import is_windows_system, md5string, load_json_cache


class Dispatcher(object):
    TPL_DISPATCHER_DEBUG_MSG = '[dispatcher] {}'

    def __init__(self):
        self._start_time = time.time()
        self._command = None
        self._builder = None
        self._logger = Logger()
        self._stop_event = threading.Event()
        self._logger_worker = LoggerWorker(self._logger, self._stop_event)
        self._task_engine = TaskEngine(self._logger)
        self._args = None
        self._config = None

    def call_command(self, args):
        self._config = read_freeline_config()
        self._args = args
        self.debug('command line args: ' + str(args))
        Logger.info('[INFO] preparing for tasks...')

        if is_windows_system() or ('debug' in args and args.debug):
            self._logger.debuggable = True
            Logger.debuggable = True

        self._check_logger_worker()

        if 'cleanBuild' in args and args.cleanBuild:
            is_build_all_projects = args.all
            wait_for_debugger = args.wait
            self._setup_clean_build_command(is_build_all_projects, wait_for_debugger)
        elif 'version' in args and args.version:
            version()
        elif 'clean' in args and args.clean:
            self._command = CleanAllCacheCommand(self._config['build_cache_dir'])
        else:
            from freeline_build import FreelineBuildCommand
            self._command = FreelineBuildCommand(self._config, task_engine=self._task_engine)

        if not isinstance(self._command, AbstractCommand):
            raise TypeError

        self._exec_command(self._command)

    def debug(self, message):
        Logger.debug(Dispatcher.TPL_DISPATCHER_DEBUG_MSG.format(message))

    def _check_logger_worker(self):
        if not self._args.version:  # and not self._args.init:
            self._logger_worker.start()

    def _join_logger_worker(self):
        if not self._args.version:  # and not self._args.init:
            self._logger_worker.join()

    def _setup_clean_build_command(self, is_build_all_projects, wait_for_debugger):
        self._builder = self._setup_clean_builder(is_build_all_projects, wait_for_debugger)
        from build_commands import CleanBuildCommand
        self._command = CleanBuildCommand(self._builder)

    def _setup_clean_builder(self, is_build_all_projects, wait_for_debugger):
        if 'project_type' in self._config:
            ptype = self._config['project_type']
            if ptype == 'gradle':
                from gradle_clean_build import GradleCleanBuilder
                return GradleCleanBuilder(self._config, self._task_engine, wait_for_debugger=wait_for_debugger)

        return None

    def _exec_command(self, command):
        footer = '[DEBUG] --------------------------------------------------------'
        is_exception = False
        try:
            command.execute()
        except CheckSyncStateException:
            is_exception = True
            self._retry_clean_build('[WARNING] check sync status failed, a clean build will be automatically executed.')
            # flush_error_info(e)
        except NoInstallationException:
            is_exception = True
            self._retry_clean_build(
                '[WARNING] NoInstallationException occurs, a clean build will be automatically executed.')
        except FileMissedException:
            is_exception = True
            self._retry_clean_build(
                '[WARNING] some important file missed, a clean build will be automatically executed.')
        except KeyboardInterrupt:
            is_exception = True
            footer = KEYBOARD_INTERRUPT_MESSAGE
            self._flush_footer(footer)
        except FreelineException as e:
            is_exception = True
            footer = EXCEPTION_ERROR_MESSAGE.format(e.cause, e.message)
            self._flush_footer(footer)
        except Exception as e:
            is_exception = True
            footer = EXCEPTION_ERROR_MESSAGE.format(traceback.format_exc(), e.message)
            log_path = Logger.write_error_log(exception=footer)
            if log_path:
                footer += '[ERROR] you can find error log in: ' + log_path
                footer += '\n[ERROR] --------------------------------------------------------'
            self._flush_footer(footer)

        if not is_exception:
            self._flush_footer(footer)

    def _retry_clean_build(self, message):
        self._logger.reset()  # reset logger
        Logger.info(message)
        Logger.debug(message)
        self._setup_clean_build_command(is_build_all_projects=False, wait_for_debugger=self._args.wait)
        self._exec_command(self._command)

    def _flush_footer(self, footer):
        self._stop_event.set()
        if self._logger.debuggable:
            time.sleep(0.1)  # hack method: wait for the logger worker to flush queue and avoid dead lock
        else:
            self._join_logger_worker()
        finished_time = time.time() - self._start_time
        print(footer.strip())
        print(FOOTER_MESSAGE.format(round(finished_time - self._task_engine.cost_time, 1),
                                    round(self._task_engine.cost_time, 1), round(finished_time, 1)).strip())


def version():
    from version import get_freeline_version
    print(get_freeline_version())
    exit()


def get_cache_dir():
    cache_dir_path = os.path.join(os.path.expanduser('~'), '.freeline', 'cache', md5string(os.getcwd().decode("utf-8")))
    if not os.path.exists(cache_dir_path):
        os.makedirs(cache_dir_path)
    return cache_dir_path


def read_freeline_config(config_path=None):
    if not config_path:
        config_path = os.path.join(get_cache_dir(), 'project_description.json')

    if os.path.isfile(config_path):
        config = load_json_cache(config_path)
        return config

    raise NoConfigFoundException(config_path)


class CleanAllCacheCommand(AbstractCommand):
    def __init__(self, cache_dir):
        AbstractCommand.__init__(self, 'clean_all_cache_command')
        from android_tools import CleanAllCacheTask
        self._invoker = CleanAllCacheTask(cache_dir)

    def execute(self):
        self.debug('start clean cache...')
        self._invoker.execute()
        self.debug('clean all cache done.')


KEYBOARD_INTERRUPT_MESSAGE = """
[DEBUG] --------------------------------------------------------
[DEBUG] Freeline KeyboardInterrupt EXIT
[DEBUG] --------------------------------------------------------
"""

EXCEPTION_ERROR_MESSAGE = """
[ERROR] --------------------------------------------------------
[ERROR] Freeline ERROR
[ERROR] --------------------------------------------------------
{}
[ERROR] --------------------------------------------------------
[ERROR] {}
[ERROR] --------------------------------------------------------
"""

FOOTER_MESSAGE = """
[DEBUG] Prepare tasks time: {}s
[DEBUG] Task engine running time: {}s
[DEBUG] Total time: {}s
[DEBUG] --------------------------------------------------------
"""
