#

SHARE := share

default: build

projectfloodlight:
	$(MAKE) -C projectfloodlight SHARE=../$(SHARE)

build: projectfloodlight build-gui
	$(MAKE) -C fl-root/floodlight-modules pre-docker SHARE=../../$(SHARE)
	# FIXME(surabujin): I believe there must not be "clean" phase.
	mvn -B clean install

build-no-test: projectfloodlight build-gui
	$(MAKE) -C fl-root/floodlight-modules pre-docker SHARE=../../$(SHARE)
	mvn -B install -DskipTests

build-pce:
	mvn install -pl pce -am -DskipTests

build-northbound:
	mvn install -pl northbound -am -DskipTests

build-messaging:
	mvn install -pl messaging -am -DskipTests

build-gui:
	$(MAKE) -C openkilda-gui

unit: projectfloodlight
	mvn -B test

clean:
	mvn clean
	$(MAKE) -C projectfloodlight clean

.PHONY: default projectfloodlight build build-gui clean
