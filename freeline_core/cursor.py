# -*- coding:utf8 -*-

from terminal import Terminal


class Cursor(object):
    def __init__(self, term=None):
        self.term = Terminal() if term is None else term
        self._stream = self.term.stream
        self._saved = False

    def write(self, s):
        self._stream.write(s)

    def save(self):
        self.write(self.term.save)
        self._saved = True

    def restore(self):
        if self._saved:
            self.write(self.term.restore)

    def flush(self):
        self._stream.flush()

    def newline(self):
        self.write(self.term.move_down)
        self.write(self.term.clear_bol)

    def clear_lines(self, num_lines=0):
        for i in range(num_lines):
            self.write(self.term.clear_eol)
            self.write(self.term.move_down)
        for i in range(num_lines):
            self.write(self.term.move_up)
