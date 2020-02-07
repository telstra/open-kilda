all: wheel

install:
	pip3 install -r requirements.txt
	python3 setup.py build install

wheel:
	python3 setup.py bdist_wheel

deploy-wheel: wheel
	cp dist/kilda_traffexam-*.whl ../../../docker/lab-service

.PHONY: all
