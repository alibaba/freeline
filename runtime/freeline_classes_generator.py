#!/usr/bin/python
# -*- coding:utf-8 -*-
from __future__ import print_function
import os


def main():
    print('====================================')
    print('      freeline code generator       ')
    print('====================================')

    target_dir = os.path.join('runtime', 'build', 'intermediates', 'classes', 'release')
    freeline_classes = []
    for dirpath, dirnames, files in os.walk(target_dir):
        for fn in files:
            if fn.endswith('.class'):
                class_name = os.path.join(dirpath, fn).replace(target_dir + '/', '')
                freeline_classes.append(class_name)

    freeline_classes.append('com/antfortune/freeline/FreelineConfig.class')

    for clazz in freeline_classes:
        print("FREELINE_CLASSES.add('{}')".format(clazz))

    print('====================================')
    print('                 end                ')
    print('====================================')


if __name__ == '__main__':
    main()
