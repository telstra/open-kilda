# 'make' will build the latest and try to run it.
default: build-latest run-dev

build-latest:
	docker build -t kilda/base-ubuntu:latest base/kilda-base-ubuntu/
	docker-compose build

run-dev:
	docker-compose up

.PHONY: default run-dev build-latest
