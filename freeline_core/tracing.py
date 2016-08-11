import time

from logger import Logger


class Tracing(object):
    def __init__(self, description):
        self.__name = "tracing"
        self.__description = description
        self.__start_time = 0

    def __enter__(self):
        self.__start_time = time.time()

    def __exit__(self, exc_type, exc_val, exc_tb):
        if exc_tb:
            return False
        else:
            self.debug(time.time() - self.__start_time)
            return True

    def debug(self, execute_time):
        Logger.debug('[{}] {}: {}ms'.format(self.__name, self.__description, execute_time * 1000))
