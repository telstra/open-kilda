# 'make' will build the latest and try to run it.
default: build-latest run-dev

build-base:
	docker build -t kilda/base-ubuntu:latest base/kilda-base-ubuntu/
	docker build -t kilda/base-floodlight:latest base/base-floodlight/

build-latest: build-base
	docker-compose build

run-dev:
	docker-compose up

.PHONY: default run-dev build-latest
