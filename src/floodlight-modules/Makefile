# Makefile to build the openkilda floodlight modules

default: install

build-modules:
	mvn package
	
install: 
	mkdir -p ../../services/openkilda/app/floodlight-modules
	cp -rv * ../../services/openkilda/app/floodlight-modules
