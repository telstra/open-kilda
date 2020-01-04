clean-pyc:
	find . -name '*.pyc' -exec rm --force {} +
	find . -name '*.pyo' -exec rm --force {} +
	find . -name '*~' -exec rm --force  {} +

test: clean-pyc
	tox -epy36,pep8

build:
	tox -epy36 --notest
	cp .tox/py36/bin/probe .

.PHONY: clean-pyc test
