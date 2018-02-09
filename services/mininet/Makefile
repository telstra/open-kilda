build:
	tox -e py27
	.tox/py27/bin/python setup.py sdist
	.tox/py27/bin/python setup.py bdist_wheel

clean:
	rm -rf .eggs .tox build dist

.PHONY: all build docker clean
