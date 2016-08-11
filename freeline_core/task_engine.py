# -*- coding:utf8 -*-
import Queue
import threading
import time
import traceback
from logger import Logger
from task import ExecutableTask, Task


class ThreadPool(object):
    def __init__(self):
        self._init_core_worker()

    def _init_core_worker(self):
        self.core_worker = CoreWorker()
        self.core_worker.setDaemon(True)

    def add_task(self, task):
        self.core_worker.add_task(task)

    def start(self):
        self.core_worker.start()

    def wait(self):
        self.core_worker.wait()


class CoreWorker(threading.Thread):
    def __init__(self, workers_num=6):
        threading.Thread.__init__(self)
        self.queue = Queue.Queue()
        self.workers = []
        self.workers_num = workers_num
        self._init_workers()

    def add_task(self, task):
        self.queue.put(task)

    def run(self):
        map(lambda thread: thread.start(), self.workers)

    def _init_workers(self):
        for i in range(0, self.workers_num):
            worker = Worker(self.queue)
            worker.setDaemon(True)
            self.workers.append(worker)

    def wait(self):
        self.queue.join()


class Worker(threading.Thread):
    def __init__(self, queue):
        threading.Thread.__init__(self)
        self.queue = queue
        self._tpl_debug_message = '[worker_thread] {}'

    def run(self):
        while True:
            try:
                task = self.queue.get()
                if isinstance(task, ExecutableTask):
                    task.execute()
            except Exception as e:
                self.debug('worker thread catch exception')
                self.debug(traceback.format_exc())
            finally:
                self.queue.task_done()

    def debug(self, message):
        Logger.debug(self._tpl_debug_message.format(message))


class TaskEngine(object):
    def __init__(self, logger):
        self._logger = logger
        self.queue = Queue.Queue()
        self.condition = threading.Condition()
        self.start_time = 0
        self.cost_time = 0

        self.pool = ThreadPool()
        self.pool.start()

        self._interrupt_exception = None
        self._init_attr()
        self.tpl_logger_message = '[task_engine] {}'

    def _init_attr(self):
        self.root_tasks = []
        self.tasks_dict = {}
        self.tasks_depth_dict = {}
        self.sorted_tasks = []

    def debug(self, message):
        Logger.debug(self.tpl_logger_message.format(message))

    def add_root_task(self, task):
        if isinstance(task, list):
            map(lambda t: self._add_root_task(t), task)
        else:
            self._add_root_task(task)

    def start(self):
        self.start_time = time.time()
        self._interrupt_exception = None
        self._prepare()
        self.wait()

        if self._interrupt_exception is not None:
            raise self._interrupt_exception

    def finish(self):
        self.cost_time = time.time() - self.start_time
        self.debug('it takes task engine {}s to execute tasks.'.format(round(self.cost_time, 2)))
        self._init_attr()
        self.notify()

    def is_all_tasks_finished(self):
        tasks = self.tasks_dict.values()
        for task in tasks:
            if task.status != 3 and task.status != -1:
                return False
        return True

    def get_running_tasks(self):
        tasks = self.tasks_dict.values()
        return [task for task in tasks if task.status != 3 and task.status != -1]

    def wait(self):
        self.condition.acquire()
        self.condition.wait()
        self.condition.release()

    def notify(self):
        self.condition.acquire()
        self.condition.notify()
        self.condition.release()

    def interrupt(self, exception):
        self._interrupt_exception = exception
        self.debug('task engine occurs exception, engine will exit.')

    def _add_root_task(self, task):
        if isinstance(task, Task) and task not in self.root_tasks:
            self.root_tasks.append(task)

    def _prepare(self):
        tasks_queue = Queue.Queue()
        for task in self.root_tasks:
            tasks_queue.put(task)

        has_added_tasks = []
        while not tasks_queue.empty():
            task = tasks_queue.get()
            has_added_tasks.append(task)

            if not self.tasks_dict.has_key(task.name):
                self.tasks_dict[task.name] = task

            for child in task.child_tasks:
                if child not in has_added_tasks:
                    tasks_queue.put(child)

        depth_array = []

        for task in self.tasks_dict.values():
            depth = TaskEngine.calculate_task_depth(task)
            if self.tasks_depth_dict.has_key(depth):
                self.tasks_depth_dict[depth].append(task)
            else:
                self.tasks_depth_dict[depth] = []
                self.tasks_depth_dict[depth].append(task)
                depth_array.append(depth)

        depth_array.sort()

        for depth in depth_array:
            tasks = self.tasks_depth_dict[depth]
            for task in tasks:
                self.debug("depth: {}, task: {}".format(depth, task))
                self.sorted_tasks.append(task)

        self._logger.set_sorted_tasks(self.sorted_tasks)

        for task in self.sorted_tasks:
            self.pool.add_task(ExecutableTask(task, self))

    @staticmethod
    def calculate_task_depth(task):
        depth = []
        parent_task_queue = Queue.Queue()
        parent_task_queue.put(task)
        while not parent_task_queue.empty():
            parent_task = parent_task_queue.get()

            if parent_task.name not in depth:
                depth.append(parent_task.name)

            for parent in parent_task.parent_tasks:
                if parent.name not in depth:
                    parent_task_queue.put(parent)

        return len(depth)
