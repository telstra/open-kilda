#!/bin/bash

set -eux
cd -- "$(dirname -- "$0")/.." || exit 1

python setup.py develop

flake8 --max-line-length=119 src

python setup.py bdist_wheel
python setup.py --version > dist/version
