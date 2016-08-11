# -*- coding:utf8 -*-
from __future__ import print_function
import os

from exceptions import FreelineException
from utils import is_windows_system, cexec, copy, get_file_content

is_windows = is_windows_system()


def init():
    project_dir = os.getcwd()
    symlink('freeline', project_dir, 'freeline.py')

    if is_windows:
        symlink('freeline', project_dir, 'freeline_core')

    from gradle_tools import get_all_modules
    modules = get_all_modules(project_dir)
    for m in modules:
        if is_main_project(m['path']):
            main_module = m
            break

    if not main_module:
        raise FreelineException('main module not found', 'set main module first')

    print('find main module: ' + main_module['name'])
    args = []
    if is_windows:
        args.append('gradlew.bat')
    else:
        args.append('./gradlew')
    args.append(':{}:checkBeforeCleanBuild'.format(main_module['name']))
    print('freeline is reading project info, please wait a moment...')
    output, err, code = cexec(args, cwd=project_dir)
    if code != 0:
        raise FreelineException('freeline failed when read project info with script: {}'.format(args),
                                '{}\n{}'.format(output, err))
    print('freeline init success')


def is_main_project(module):
    config_path = os.path.join(module, 'build.gradle')
    if os.path.exists(config_path):
        content = get_file_content(config_path)
        if "apply plugin: 'com.antfortune.freeline'" in content:
            return True
    return False


def symlink(base_dir, target_dir, fn):
    base_path = os.path.join(base_dir, fn)
    target_path = os.path.join(target_dir, fn)

    if not os.path.exists(base_path):
        raise FreelineException('file missing: {}'.format(base_path), '     Maybe you should sync freeline repo')

    if os.path.exists(target_path):
        os.remove(target_path)

    if is_windows_system():
        copy(base_path, target_path)
    else:
        os.symlink(base_path, target_path)
