all:
	tox -e py35
	.tox/py35/bin/python setup.py sdist
	.tox/py35/bin/python setup.py bdist_wheel

	mkdir -p ../mininet/py-deps
	cp dist/* ../mininet/py-deps
