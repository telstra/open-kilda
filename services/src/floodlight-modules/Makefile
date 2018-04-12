# Makefile to build the openkilda floodlight modules

pre-docker: run-deps/floodlight.jar

run-deps/floodlight.jar: $(SHARE)/build-artifact/floodlight.jar
	cp $< $@

.PHONY: pre-docker
