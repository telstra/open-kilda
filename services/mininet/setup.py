#!/usr/bin/env python

import functools
import os
from setuptools import find_packages
import sys

from setuptools import setup

# import kilda.mininet

project_root = os.path.dirname(__file__)
project_root = os.path.abspath(project_root)
project_root = os.path.normpath(project_root)

project_path = functools.partial(os.path.join, project_root)

requirements = open(project_path('requirements.txt')).readlines()

needs_pytest = bool({'pytest', 'test', 'ptr'}.intersection(sys.argv))
pytest_runner = ['pytest-runner'] if needs_pytest else []

setup(
    name='kilda-mininet',
    version='0.0.1', # kilda.mininet.__version__,
    description='link load generator',
    long_description=open(project_path('README.rst')).read(),
    author='Dmitry Bogun',
    author_email='bogun.dmitriy@gmail.com',
    packages=find_packages(),
    setup_requires=pytest_runner,
    tests_require=['pytest'],
    install_requires=requirements,
    entry_points={
        'console_scripts': [
                'kilda-mininet-rest = kilda.mininet.rest:main',
                'kilda-mininet-flow-tool = kilda.mininet.flow_tool:main']}
)
