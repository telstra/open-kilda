#!/usr/bin/env python

import functools
import json
import os

from setuptools import find_packages
from setuptools import setup

root = os.path.dirname(__file__)
root = os.path.abspath(root)
root = os.path.normpath(root)

path = functools.partial(os.path.join, root)

setup_deps = ['wheel']
with open(path('requirements.txt')) as f:
    run_deps = f.readlines()
with open(path('requirements-dev.txt')) as f:
    test_deps = f.readlines()

with open(path('README.md'), 'rt') as f:
    README = f.read()

with open(path('src', 'kilda', 'tsdb_dump_restore', 'APP_META.json'), 'rt') as f:
    meta = json.load(f)

setup(
    name='kilda-otsdb-dump-restore',
    version=meta['version'],
    description='Provide tools to dump and restore OpenTSDB data',
    long_description=README,
    long_description_content_type='text/markdown',
    packages=find_packages('src'),
    package_dir={'': 'src'},
    package_data={'': ['*.json']},
    setup_requires=setup_deps,
    tests_require=test_deps,
    install_requires=run_deps,
    entry_points={
        'console_scripts': [
            'kilda-otsdb-dump = kilda.tsdb_dump_restore.dump:main',
            'kilda-otsdb-restore = kilda.tsdb_dump_restore.restore:main']},
    classifiers=[
        'Programming Language :: Python :: 3'
    ],
)
