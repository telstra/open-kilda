all: install

test:
	tox -e pep8,py3

install:
	pip3 install -r requirements.txt
	python3 setup.py build install

clean:
	rm -rf .eggs .tox build dist

.PHONY: all
