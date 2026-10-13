"""Restricted computation environment backed by CPython; no filesystem or host objects.

Guest code accepts and returns JSON. Python's full unrestricted standard library is
intentionally unavailable. Each invocation has fresh module state.
"""
import ast
import builtins
import json
import math
import sys
import time
import types

ATTRIBUTES = frozenset(('append', 'extend', 'pop', 'items', 'keys', 'values', 'get',
    'upper', 'lower', 'split', 'join', 'strip', 'lstrip', 'rstrip', 'replace',
    'count', 'index', 'startswith', 'endswith', 'sort', 'reverse', 'copy',
    'add', 'discard', 'remove', 'update', 'clear', 'insert', 'isdigit', 'isalpha',
    'sqrt', 'sin', 'cos', 'tan', 'floor', 'ceil', 'fabs', 'log', 'log10', 'exp',
    'pow', 'isfinite', 'isnan', 'pi', 'e', 'inf', 'factorial', 'gcd', 'hypot'))
SAFE_BUILTINS = {name: getattr(builtins, name) for name in (
    'abs', 'all', 'any', 'bool', 'dict', 'divmod', 'enumerate', 'filter', 'float',
    'int', 'isinstance', 'len', 'list', 'map', 'max', 'min', 'next', 'range',
    'reversed', 'round', 'set', 'slice', 'sorted', 'str', 'sum', 'tuple', 'zip',
    'Exception', 'ValueError', 'TypeError', 'KeyError', 'IndexError', 'ZeroDivisionError')}

class Guard(ast.NodeVisitor):
    def visit_Attribute(self, node):
        if node.attr not in ATTRIBUTES:
            raise ValueError('Attribute is unavailable in Annie Python: ' + node.attr)
        self.generic_visit(node)

    def visit_Name(self, node):
        if node.id.startswith('_'):
            raise ValueError('Private names are unavailable in Annie Python')

    def visit_ClassDef(self, node):
        raise ValueError('Classes are unavailable in this computation runtime')

    def visit_Import(self, node):
        for alias in node.names:
            if alias.name != 'math':
                raise ValueError('Only the math module is available')
        self.generic_visit(node)

    def visit_ImportFrom(self, node):
        if node.module != 'math' or node.level or any(a.name not in ATTRIBUTES for a in node.names):
            raise ValueError('Only public math functions are available')
        self.generic_visit(node)

    def visit_Global(self, node):
        raise ValueError('Global declarations are unavailable')

    def visit_AsyncFunctionDef(self, node):
        raise ValueError('Use synchronous functions; Annie awaits the worker')


def run(source, filename, function, input_json):
    if len(source) > 256000 or len(input_json) > 64000:
        raise ValueError('Python input exceeds the runtime limit')
    if not function.isidentifier() or function.startswith('_'):
        raise ValueError('Invalid Python function name')
    tree = ast.parse(source, filename=filename)
    Guard().visit(tree)
    safe_math = types.SimpleNamespace(**{name: getattr(math, name) for name in ATTRIBUTES if hasattr(math, name)})
    def guest_import(name, *args, **kwargs):
        if name != 'math':
            raise ValueError('Module unavailable: ' + name)
        return safe_math
    environment = {'__builtins__': dict(SAFE_BUILTINS, __import__=guest_import)}
    deadline = time.monotonic() + 5
    budget = 1000000
    def trace(frame, event, arg):
        nonlocal budget
        budget -= 1
        if budget <= 0 or time.monotonic() >= deadline:
            raise TimeoutError('Python execution exceeded its time limit')
        return trace
    previous = sys.gettrace()
    try:
        sys.settrace(trace)
        exec(compile(tree, filename, 'exec'), environment, environment)
        handler = environment.get(function)
        if not callable(handler):
            raise ValueError('Missing Python function: ' + function)
        result = handler(json.loads(input_json))
        encoded = json.dumps(result, ensure_ascii=True, allow_nan=False)
        if len(encoded) > 64000:
            raise ValueError('Python result exceeds the runtime limit')
        return encoded
    finally:
        sys.settrace(previous)
