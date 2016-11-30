# -*- coding:utf8 -*-
from __future__ import print_function
import os
import re
import time
import datetime

import android_tools
from builder import Builder
from logger import Logger
from utils import cexec, curl, get_file_content, write_file_content

NO_DEVICE_FOUND_MESSAGE = """\tPlease make sure your application is properly running in your device.
\tCheck follow steps:
\t1. Make sure the versions `python freeline.py -v`, freeline-gradle and freeline-runtime are the same;
\t2. Make sure there is no network proxy.
\t
\tMore about this can see: https://github.com/alibaba/freeline/issues/152"""


PORT_START = 41128


class SyncClient(object):
    def __init__(self, is_art, config):
        self._is_art = is_art
        self._config = config
        self._adb = Builder.get_adb(self._config)
        self._cache_dir = self._config['build_cache_dir']
        self._port = 0

    def debug(self, message):
        Logger.debug('[sync_client] {}'.format(message))

    def check_device_connection(self):
        commands = [self._adb, 'devices']
        output, err, code = cexec(commands, callback=None)
        if code == 0:
            devices = output.strip().split('\n')
            length = len(devices)
            from exceptions import UsbConnectionException
            if length < 2:
                self.debug('No device\'s connection found')
                raise UsbConnectionException('No device\'s connection found',
                                             '\tUse `adb devices` to check your device connection')
            if length > 2:
                self.debug('More than 1 devices connect:')
                self.debug(devices)
                raise UsbConnectionException('More than 1 devices connect',
                                             '\tOnly 1 device allowed, '
                                             'use `adb devices` to check your devices\' connection')

    def check_installation(self):
        commands = [self._adb, 'shell', 'pm', 'list', 'packages', self._config['debug_package']]
        output, err, code = cexec(commands, callback=None)
        result = re.findall(self._config['debug_package'].replace('.', '\.') + '\s+\Z', output)
        if len(result) != 1:
            self.debug('No package named {} been installed to your device'.format(self._config['package']))
            from exceptions import NoInstallationException
            raise NoInstallationException(
                'No package named {} been installed to your device'.format(self._config['debug_package']),
                '\tUse `adb shell pm list packages {}` to check app installation.'.format(
                    self._config['debug_package']))

    def ensure_device_status(self):
        if not self._check_screen_status():
            self.debug('try to turn on your device\'s screen...')
            self._turn_on_screen()

    def connect_device(self):
        self.debug('start to connect device...')
        sync_value, uuid = self._get_check_values()
        self._port = self.scan_device_port(sync_value, uuid)

        if self._port == 0:
            for i in range(1, 11):
                need_protection = i <= 1
                self.wake_up(need_protection=need_protection)
                self._port = self.scan_device_port(sync_value, uuid)
                if self._port != 0:
                    break
                time.sleep(0.2)
                self.debug('try to connect device {} times...'.format(i))

        if self._port == 0:
            self.check_device_connection()
            self.check_installation()
            message = 'Freeline server in app {} not found. Please make sure your application is properly running in ' \
                      'your device.'.format(self._config['debug_package'])
            self.debug(message)
            from exceptions import NoDeviceFoundException
            raise NoDeviceFoundException(message, NO_DEVICE_FOUND_MESSAGE)
        self.debug('find device port: {}'.format(self._port))

    def close_connection(self):
        if self._port != 0:
            cexec([self._adb, 'forward', '--remove', 'tcp:{}'.format(self._port)], callback=None)

    def scan_device_port(self, sync_value, uuid):
        port = 0

        for i in range(0, 10):
            cexec([self._adb, 'forward', 'tcp:{}'.format(PORT_START + i), 'tcp:{}'.format(PORT_START + i)],
                  callback=None)
            url = 'http://127.0.0.1:{}/checkSync?sync={}&uuid={}'.format(PORT_START + i, sync_value, uuid)
            result, err, code = curl(url)
            if code == 0 and result is not None:
                result = int(result)
                self.debug('server result is {}'.format(result))
                if result == 0:
                    self.debug('check sync value failed, maybe you need a clean build.')
                    from exceptions import CheckSyncStateException
                    raise CheckSyncStateException('check sync value failed, maybe you need a clean build.',
                                                  'NO CAUSE')
                elif result == -1:
                    continue
                else:
                    port = PORT_START + i
                    break

        for i in range(0, 10):
            if (PORT_START + i) != port:
                cexec([self._adb, 'forward', '--remove', 'tcp:{}'.format(PORT_START + i)], callback=None)

        return port

    def _get_check_values(self):
        apktime_path = self._get_apktime_path()
        self.debug("apktime path: " + apktime_path)
        sync_value = get_sync_value(apktime_path, self._cache_dir)
        self.debug('your local sync value is: {}'.format(sync_value))
        uuid = get_apk_created_ticket(apktime_path)
        self.debug('your local uuid value is: {}'.format(uuid))
        return sync_value, uuid

    def sync_incremental_res(self):
        raise NotImplementedError  # TODO: sync single res.pack

    def sync_incremental_native(self):
        raise NotImplementedError

    def sync_incremental_dex(self):
        # dex_path = android_tools.get_incremental_dex_path(self._cache_dir)
        dex_dir = android_tools.get_incremental_dex_dir(self._cache_dir)
        if os.path.isdir(dex_dir):
            dexes = [fn for fn in os.listdir(dex_dir) if fn.endswith('.dex')]
            if len(dexes) > 0:
                self.debug('start to sync incremental dex...')
                for dex_name in dexes:
                    dex_path = os.path.join(dex_dir, dex_name)
                    with open(dex_path, 'rb') as fp:
                        url = 'http://127.0.0.1:{}/pushDex?dexName={}'.format(self._port, dex_name.replace('.dex', ''))
                        self.debug('pushdex: ' + url)
                        self.debug('dex path: {}'.format(dex_path))
                        result, err, code = curl(url, body=fp.read())
                        if code != 0:
                            from exceptions import FreelineException
                            raise FreelineException('sync incremental dex failed.', err.message)
            else:
                self.debug('no incremental dexes in {}'.format(dex_dir))

    def sync_state(self, is_need_restart):
        if self._is_need_sync_dex() or self._is_need_sync_res() or self._is_need_sync_native():
            self.debug('start to sync close longlink...')
            restart_char = 'restart' if is_need_restart else 'no'
            if self._is_need_sync_native():
                restart_char = 'restart'
            update_last_sync_ticket(self._cache_dir)
            url = 'http://127.0.0.1:{}/closeLongLink?{}&lastSync={}'.format(self._port, restart_char,
                                                                            get_last_sync_ticket(self._cache_dir))
            self.debug('closeLongLink: ' + url)
            result, err, code = curl(url)
            # self.wake_up()
            if code != 0:
                rollback_last_sync_ticket(self._cache_dir)
                from exceptions import FreelineException
                raise FreelineException('sync state failed.', err.message)

    def get_uuid(self):
        from utils import md5string
        if 'debug_package' in self._config and self._config['debug_package'] != '':
            return md5string(self._config['debug_package'])
        else:
            return md5string(self._config['package'])

    def wake_up(self, need_protection=False):
        package = self._config['package']
        if 'debug_package' in self._config:
            package = self._config['debug_package']

        wake_up_args = [self._adb, 'shell', 'am', 'startservice', '-n',
                        '{}/{}'.format(package, 'com.antfortune.freeline.FreelineService')]
        if not need_protection:
            wake_up_args.extend(['-e', 'wakeup', 'marker'])
        self.debug('wake up Service: {}'.format(' '.join(wake_up_args)))
        cexec(wake_up_args, callback=None)

    def _check_screen_status(self):
        commands = [self._adb, 'shell', 'dumpsys', 'input_method']
        check_str = 'mInteractive=true' if self._is_art else 'mScreenOn=true'
        output, err, code = cexec(commands, callback=None)
        return re.search(check_str, output)

    def _turn_on_screen(self):
        commands = [self._adb, 'shell', 'input', 'keyevent', '26']
        cexec(commands, callback=None)

    def _get_apktime_path(self):
        raise NotImplementedError

    def _is_need_sync_dex(self):
        return os.path.exists(android_tools.get_incremental_dex_path(self._cache_dir))

    def _is_need_sync_res(self):
        raise NotImplementedError

    def _is_need_sync_native(self):
        raise NotImplementedError


def get_sync_value(apktime_path, cache_dir):
    return get_apk_created_ticket(apktime_path) + get_last_sync_ticket(cache_dir)


def get_last_sync_ticket_path(cache_dir):
    return os.path.join(cache_dir, 'syncid')


def get_last_sync_ticket(cache_dir):
    ticket_path = get_last_sync_ticket_path(cache_dir)
    data = get_file_content(ticket_path)
    return 0 if len(data) == 0 else int(data)


def update_last_sync_ticket(cache_dir):
    ticket = get_last_sync_ticket(cache_dir) + 1
    ticket_path = get_last_sync_ticket_path(cache_dir)
    write_file_content(ticket_path, ticket)


def rollback_last_sync_ticket(cache_dir):
    ticket = get_last_sync_ticket(cache_dir)
    if ticket > 0:
        ticket -= 1
    else:
        ticket = 0
    write_file_content(get_last_sync_ticket_path(cache_dir), ticket)


def get_apk_created_ticket(apktime_path):
    data = get_file_content(apktime_path)
    return 0 if len(data) == 0 else int(data)


def update_clean_build_created_flag(apktime_path):
    flag = str(datetime.datetime.now().microsecond)
    Logger.debug("update apk time path: " + apktime_path)
    Logger.debug("new clean build flag value: " + flag)
    write_file_content(apktime_path, flag)
