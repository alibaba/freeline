# -*- coding:utf8 -*-


class NoConfigFoundException(Exception):
    def __init__(self, path):
        Exception.__init__(self, '{} not found, please execute gradlew checkBeforeCleanBuild first.'.format(path))


class EnvironmentException(Exception):
    def __init__(self, message):
        Exception.__init__(self, message)


class FreelineException(Exception):
    def __init__(self, message, cause):
        Exception.__init__(self, message)
        self.cause = cause


class NoDeviceFoundException(FreelineException):
    def __init__(self, message, cause):
        FreelineException.__init__(self, message, cause)


class CheckSyncStateException(FreelineException):
    def __init__(self, message, cause):
        FreelineException.__init__(self, message, cause)


class NoInstallationException(FreelineException):
    def __init__(self, message, cause):
        FreelineException.__init__(self, message, cause)


class FileMissedException(FreelineException):
    def __init__(self, message, cause):
        FreelineException.__init__(self, message, cause)


class UsbConnectionException(FreelineException):
    def __init__(self, message, cause):
        FreelineException.__init__(self, message, cause)
