# NB: not sure these variables are needed yet .. they give indepenance from where
# 		make is called
mkfile_path := $(abspath $(lastword $(MAKEFILE_LIST)))
current_dir := $(notdir $(patsubst %/,%,$(dir $(MAKEFILE_LIST))))
docker_sock := /var/run/docker.sock:/var/run/docker.sock

# These constants are used in the targets below
GROUP := kilda
IMAGE := $(GROUP)/builder-java
VER := 0.0.2

build:
	docker build -t $(IMAGE):$(VER) -t $(IMAGE):latest .

test:
	docker run --rm -v $(PWD)/tests:/app -w /app $(IMAGE):$(VER) javac Main.java

run:
	docker run --rm -v $(PWD)/tests:/app -w /app $(IMAGE):$(VER) java Main

wtf:
	@echo $(PWD)/$(current_dir)

.PHONY: build test run wtf
