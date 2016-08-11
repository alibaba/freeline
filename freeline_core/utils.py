# -*- coding:utf8 -*-
from __future__ import print_function
import os
import string
import random
import platform
import re
import hashlib
import json
import shutil
from hashlib import md5
from subprocess import Popen, PIPE

import errno

try:
    import xml.etree.cElementTree as ET
except ImportError:
    import xml.etree.ElementTree as ET


def cexec(args, callback=None, add_path=None, cwd=None):
    env = None
    if add_path:
        import copy
        env = copy.copy(os.environ)
        env['PATH'] = add_path + os.path.pathsep + env['PATH']
    p = Popen(args, stdin=PIPE, stdout=PIPE, stderr=PIPE, env=env, cwd=cwd)
    output, err = p.communicate()
    code = p.returncode

    if code != 0 and callback:
        callback(args, code, output, err)
    else:
        return output, err, code


def curl(url, body=None):
    code = 0
    err = None
    result = None
    try:
        import urllib2
        result = urllib2.urlopen(url, data=body).read().decode('utf-8').strip()
    except Exception as e:
        code = -1
        err = e
    return result, err, code


def generate_random_string(length=6):
    return ''.join(random.SystemRandom().choice(string.ascii_lowercase + string.digits) for _ in range(length))


def is_windows_system():
    return 'Windows' in platform.system()


def is_linux_system():
    return 'Linux' in platform.system()


def copy(src, dst):
    try:
        shutil.copytree(src, dst)
    except OSError as e:
        if e.errno == errno.ENOTDIR:
            shutil.copy(src, dst)
        else:
            print('Directory not copied. Error: {}'.format(e.message))


def get_md5(fpath):
    m = md5()
    target_file = open(fpath, 'rb')
    m.update(target_file.read())
    target_file.close()
    return m.hexdigest()


def md5string(param):
    m = hashlib.md5()
    m.update(param.encode('utf-8'))
    return m.hexdigest()


base = [str(x) for x in range(10)] + [chr(x) for x in range(ord('A'), ord('A') + 6)]


def dec2hex(string_num):
    num = int(string_num)
    mid = []
    while True:
        if num == 0:
            break
        num, rem = divmod(num, 16)
        mid.append(base[rem])
    return ''.join([str(t) for t in mid[::-1]])


def is_exe(path):
    return os.path.isfile(path) and os.access(path, os.X_OK)


def print_json(json_obj):
    print(json.dumps(json_obj, indent=4, separators=(',', ': ')))


def get_file_content(path):
    if not path or not os.path.isfile(path):
        return ''
    import codecs
    with codecs.open(path, encoding='utf-8') as f:
        return f.read()


def write_file_content(target_path, content):
    with open(target_path, 'w') as fp:
        if isinstance(content, int):
            content = unicode(content)
        fp.write(content.encode('utf-8'))


def load_json_cache(fpath):
    cache = {}
    if not os.path.exists(fpath):
        pass
    if os.path.isfile(fpath):
        try:
            with open(fpath, 'r') as fp:
                cache = json.load(fp)
        except Exception:
            pass
    return cache


def write_json_cache(fpath, cache):
    try:
        with open(fpath, 'w') as fp:
            json.dump(cache, fp)
    except Exception:
        pass


def calculate_typed_file_count(dir_path, ext):
    i = 0
    for dirpath, dirnames, files in os.walk(dir_path):
        for fn in files:
            if fn.endswith(ext):
                i += 1
    return i


def remove_namespace(path):
    content = get_file_content(path)
    xmlstring = re.sub('xmlns="[^"]+"', '', content, count=1)
    return xmlstring.encode('utf-8')  # to avoid UnicodeEncodeError


def merge_xml(filenames):
    return ET.tostring(XMLCombiner(filenames).combine().getroot())


def get_text_by_tag(root, tag):
    element = root.find(tag)
    return element.text if element is not None else ''


class HashableDict(dict):
    def __hash__(self):
        return hash(tuple(sorted(self.items())))


class XMLCombiner(object):
    def __init__(self, filenames):
        assert len(filenames) > 0, 'No filenames!'
        self.roots = [ET.parse(f).getroot() for f in filenames]

    def combine(self):
        for r in self.roots[1:]:
            # combine each element with the first one, and update that
            self.combine_element(self.roots[0], r)
        # return the string representation
        return ET.ElementTree(self.roots[0])

    def combine_element(self, one, other):
        mapping = {(el.tag, HashableDict(el.attrib)): el for el in one}
        for el in other:
            if len(el) == 0:
                try:
                    # Update the text
                    mapping[(el.tag, HashableDict(el.attrib))].text = el.text
                except KeyError:
                    # An element with this name is not in the mapping
                    mapping[(el.tag, HashableDict(el.attrib))] = el
                    # Add it
                    one.append(el)
            else:
                try:
                    # Recursively process the element, and update it in the same way
                    self.combine_element(mapping[(el.tag, HashableDict(el.attrib))], el)
                except KeyError:
                    mapping[(el.tag, HashableDict(el.attrib))] = el
                    one.append(el)
