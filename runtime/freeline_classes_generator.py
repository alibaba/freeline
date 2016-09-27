#!/usr/bin/python
# -*- coding:utf-8 -*-
from __future__ import print_function
import os


def main():
    print('====================================')
    print('      freeline code generator       ')
    print('====================================')

    target_dir = os.path.join('runtime', 'src', 'main', 'java')
    freeline_classes = []
    for dirpath, dirnames, files in os.walk(target_dir):
        for fn in files:
            if fn.endswith('.java'):
                class_name = os.path.join(dirpath, fn.replace('.java', '.class')).replace(target_dir + '/', '')
                freeline_classes.append(class_name)
                if fn == 'FreelineService.java':
                    freeline_classes.append(class_name.replace('.class', '$InnerService.class'))

    freeline_classes.append('com/antfortune/freeline/BuildConfig.class')
    freeline_classes.append('com/antfortune/freeline/FreelineConfig.class')

    for clazz in freeline_classes:
        print("FREELINE_CLASSES.add('{}')".format(clazz))

    print('====================================')
    print('                 end                ')
    print('====================================')


if __name__ == '__main__':
    main()
