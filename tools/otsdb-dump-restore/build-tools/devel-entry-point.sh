#!/bin/bash

cd -- "$(dirname -- "$0")/.." || exit 1

python setup.py develop

pip install pydevd-pycharm~=203.7148.57

echo
echo Use \"kilda-otsdb-dump\" or \"kilda-otsdb-restore\" to run application
echo

exec bash -l
