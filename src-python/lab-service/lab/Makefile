all: install

test:
	tox -e pep8,py3

install:
	pip3 install -r requirements.txt
	python3 setup.py build install

wheel:
	python3 setup.py bdist_wheel

deploy-wheel: wheel
	cp dist/kilda_lab-*.whl ../../../docker/lab-service


clean:
	rm -rf .eggs .tox build dist

.PHONY: all
.PHONY: deploy-wheel wheel
