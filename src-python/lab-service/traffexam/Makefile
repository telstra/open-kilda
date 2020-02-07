all: wheel

install:
	pip3 install -r requirements.txt
	python3 setup.py build install

wheel:
	python3 setup.py bdist_wheel

.PHONY: all
