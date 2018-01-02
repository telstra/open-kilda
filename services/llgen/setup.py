#!/usr/bin/env python

import functools
import os
from setuptools import find_packages
import sys

from setuptools import setup

from kilda import llgen

project_root = os.path.dirname(__file__)
project_root = os.path.abspath(project_root)
project_root = os.path.normpath(project_root)

project_path = functools.partial(os.path.join, project_root)

requirements = open(project_path('requirements.txt')).readlines()

needs_pytest = bool({'pytest', 'test', 'ptr'}.intersection(sys.argv))
pytest_runner = ['pytest-runner'] if needs_pytest else []

setup(
    name='kilda-llgen',
    version=llgen.__version__,
    description='link load generator',
    long_description=open(project_path('README.rst')).read(),
    author='Dmitry Bogun',
    author_email='bogun.dmitriy@gmail.com',
    packages=find_packages(),
    setup_requires=pytest_runner,
    tests_require=['pytest'],
    install_requires=requirements,
    entry_points={
        'console_scripts': ['llgen = kilda.llgen.app:cli']}
)
