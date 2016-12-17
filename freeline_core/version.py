# -*- coding:utf8 -*-
import subprocess
import os

VERSION_FORMATTER = '{}({})'
FREELINE_VERSION = 'v0.8.4'


def get_freeline_version():
    if is_git_dir():
        return VERSION_FORMATTER.format(FREELINE_VERSION, get_git_short_version())
    else:
        return FREELINE_VERSION


def get_git_short_version():
    # note: get git version
    # http://stackoverflow.com/questions/14989858/get-the-current-git-hash-in-a-python-script
    return subprocess.check_output(['git', 'rev-parse', '--short', 'HEAD'])


def is_git_dir():
    project_root_path = os.path.abspath(os.path.join(os.path.realpath(__file__), os.pardir))
    git_dir_path = os.path.join(project_root_path, '.git')
    if os.path.exists(git_dir_path) and os.path.isdir(git_dir_path):
        return True
    return False
