#!/usr/bin/env python

import functools
import re
import json
import os

from setuptools import find_packages
from setuptools import setup

root = os.path.dirname(__file__)
root = os.path.abspath(root)
root = os.path.normpath(root)

path = functools.partial(os.path.join, root)

with open(path('README.md'), 'rt') as f:
    README = f.read()


def find_dependency_links(raw, links):
    results = []
    for entry in raw:
        try:
            left, right = entry.split('#')
            if not left.split():
                continue
        except ValueError:
            pass

        entry = entry.strip()
        if not entry:
            continue

        m = re.match('https?://.*#egg=(.+)$', entry)
        if m is None:
            results.append(entry)
        else:
            results.append(m.group(1))
            links.append(entry)

    return results


deps_links = []
run_deps = find_dependency_links(
    open(path('requirements.txt')).readlines(), deps_links)
test_deps = find_dependency_links(
    open(path('requirements-dev.txt')).readlines(), deps_links)
setup_deps = ['wheel']

with open(path(
        'src', 'kilda', 'history_migration', 'APP_META.json'), 'rt') as f:
    meta = json.load(f)

setup(
    name=meta['name'],
    version=meta['version'],
    description=(
        'Toolset to migrate kilda history data between orient and mysql '
        'databases.'),
    long_description=README,
    long_description_content_type='text/markdown',
    packages=find_packages('src'),
    package_dir={'': 'src'},
    package_data={'': ['*.json']},
    setup_requires=setup_deps,
    tests_require=test_deps,
    install_requires=run_deps,
    dependency_links=deps_links,
    entry_points={
        'console_scripts': [
            'kilda-history-migration = kilda.history_migration.cli:main']},
    classifiers=[
        'Programming Language :: Python :: 3'
    ],
)
