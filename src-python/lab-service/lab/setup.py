#!/usr/bin/env python

import functools
import os
from setuptools import find_packages
import sys

from setuptools import setup

root = os.path.dirname(__file__)
root = os.path.abspath(root)
root = os.path.normpath(root)

path = functools.partial(os.path.join, root)

run_deps = open(path('requirements.txt')).readlines()
test_deps = open(path('test-requirements.txt')).readlines()
setup_deps = []

if {'pytest', 'test', 'ptr'}.intersection(sys.argv):
    setup_deps.append('pytest-runner')

setup(
    name='kilda-lab',
    version='0.1',
    author='Ilya Moiseev',
    description='REST facade to OVS testing environments',
    packages=find_packages(),
    setup_requires=setup_deps,
    tests_require=test_deps,
    install_requires=run_deps,
    entry_points={
        'console_scripts': ['kilda-lab-api = api.api:main',
                            'kilda-lab-service = service.service:main']}
)
