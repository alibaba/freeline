#!/usr/bin/python
# -*- coding:utf-8 -*-
from __future__ import print_function
import sys
from argparse import ArgumentParser

from freeline_core.dispatcher import Dispatcher
from freeline_core.init import init


class Freeline(object):
    def __init__(self):
        self.dispatcher = Dispatcher()

    def call(self, args=None):
        if 'init' in args and args.init:
            print('init freeline project...')
            init()
            exit()

        self.dispatcher.call_command(args)


def get_parser():
    parser = ArgumentParser()
    parser.add_argument('-v', '--version', action='store_true', help='show version')
    parser.add_argument('-f', '--cleanBuild', action='store_true', help='force to execute a clean build')
    parser.add_argument('-w', '--wait', action='store_true', help='make application wait for debugger')
    parser.add_argument('-a', '--all', action='store_true',
                        help="together with '-f', freeline will force to clean build all projects.")
    parser.add_argument('-c', '--clean', action='store_true', help='clean cache directory and workspace')
    parser.add_argument('-d', '--debug', action='store_true', help='show freeline debug output (NOT DEBUG APPLICATION)')
    # parser.add_argument('-i', '--init', action='store_true', help='init freeline project')
    parser.parse_args()
    return parser


def main():
    if sys.version_info > (3, 0):
        print('Freeline only support Python 2.7+ now. Please use the correct version of Python for freeline.')
        exit()

    parser = get_parser()
    args = parser.parse_args()
    freeline = Freeline()
    freeline.call(args=args)


if __name__ == '__main__':
    main()
