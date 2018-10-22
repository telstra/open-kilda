all: build

build:
	tox -e pep8,py35
	.tox/py35/bin/python setup.py bdist_wheel

build36:
	tox -e pep8,py36
	.tox/py36/bin/python setup.py bdist_wheel

clean:
	rm -rf .eggs .tox build dist

.PHONY: all
