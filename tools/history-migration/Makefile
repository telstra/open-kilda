#!/usr/bin/env make
# Makefile (root)

# Targets description:
# prod - build a container image with the application, labels it with $(DOCKER_LABEL)
# devel - build container and starts an interactive shell inside it (app dir is attached inside the container as a
#         volume, so all changes made to the application code are available inside the container without the need to
#         restart/rebuild it). Useful for the application debug.

APP_NAME := kilda-history-migration
WHEEL_NAME = $(subst -,_,$(APP_NAME))
DOCKER_LABEL = $(APP_NAME)
ID_DIR := build
DIST_DIR := dist

SRC_DIR := src
SRC_DIR_PY = $(shell find $(SRC_DIR) -type d \! -name __pycache__ -print)
SRC = requirements*.txt setup.py build-tools/* $(wildcard $(addsuffix /*.py,$(SRC_DIR_PY))) $(shell find $(SRC_DIR) -name APP_META\* -print)

all: prod

prod: $(ID_DIR)/prod.iid
	docker image tag "$$(cat $<)" "$(DOCKER_LABEL)"

dist/version: $(ID_DIR)/wheel.cid
	docker cp "$$(cat $^):/kilda/dist/version" dist/version.tmp
	docker cp "$$(cat $^):/kilda/dist/$(WHEEL_NAME)-$$(cat dist/version.tmp)-py3-none-any.whl" dist/
	mv dist/version.tmp $@

$(ID_DIR)/wheel.cid: $(ID_DIR)/wheel.iid | $(DIST_DIR)
	if [ -f "$@" ]; then docker rm "$$(cat $@)" && rm "$@"; fi
	docker run --cidfile=$@ "$$(cat $^)" /kilda/build-tools/make_wheel.sh

$(ID_DIR)/devel.cid: $(ID_DIR)/devel.iid
	if [ -f "$@" ]; then docker rm "$$(cat $@)" && rm "$@"; fi
	docker create -ti \
		--volume="/etc/resolv.conf:/etc/resolv.conf" \
		--volume="$$PWD:/kilda" \
		--network=host \
		--cidfile=$@ "$$(cat $^)" /kilda/build-tools/devel-entry-point.sh

$(ID_DIR)/devel.iid: Dockerfile.devel | $(ID_DIR)
	docker build --iidfile=$@ --file=$< \
		--build-arg=devel_user_name=$$(id -un) \
		--build-arg=devel_uid=$$(id -u) \
		--build-arg=devel_gid=$$(id -g) \
		.

$(ID_DIR)/wheel.iid: Dockerfile.devel $(SRC) | $(ID_DIR)
	docker build --iidfile=$@ --file=$< .

$(ID_DIR)/prod.iid: Dockerfile.prod dist/version $(APP_NAME).ini | $(ID_DIR)
	docker build --iidfile=$@ --file=$< \
		--build-arg=wheel="$(WHEEL_NAME)-$$(cat dist/version)-py3-none-any.whl" \
		.

$(ID_DIR):
	mkdir -p $@
$(DIST_DIR):
	mkdir -p $@

.PHONY: all prod
