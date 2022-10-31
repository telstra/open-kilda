# 'make' will build docker images and try to run it.
default: update-props build-stable up-stable

.PHONY: update-props update-props-blue update-props-green update-props-dryrun build-confd

CONFD_DOCKER_COMMAND := docker run --rm -v $(shell pwd):$(shell pwd) -w $(shell pwd) -u $(shell id -u):$(shell id -g) kilda/confd

UPDATE_PROPS := $(CONFD_DOCKER_COMMAND) confd -onetime -confdir ./confd/ -backend file -file ./confd/vars/main.yaml -file ./confd/vars/docker-compose.yaml -file ./confd/vars/test-vars.yaml -sync-only

KILDA_CONFD_IMAGE=$(shell docker images --filter=reference="kilda/confd" --format="{{.Repository}}")

ifneq "$(KILDA_CONFD_IMAGE)" "kilda/confd"
build-confd:
	docker build -t kilda/confd:latest docker/confd
BUILD_CONFD= build-confd
else
BUILD_CONFD=
endif

generated.mk update-props update-props-blue: $(BUILD_CONFD)
	$(UPDATE_PROPS) -file ./confd/vars/blue-mode.yaml

update-props-green: $(BUILD_CONFD)
	$(UPDATE_PROPS) -file ./confd/vars/green-mode.yaml

update-props-dryrun: $(BUILD_CONFD)
	$(UPDATE_PROPS) -noop

include generated.mk
