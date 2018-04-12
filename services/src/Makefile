#

SHARE := share

default: build

projectfloodlight:
	$(MAKE) -C projectfloodlight SHARE=../$(SHARE)

build: projectfloodlight
	$(MAKE) -C floodlight-modules pre-docker SHARE=../$(SHARE)
	# FIXME(surabujin): I believe there must not be "clean" phase.
	mvn -B clean install

build-pce:
	mvn install -pl pce -am -DskipTests

build-atdd:
	mvn install -pl atdd -am -DskipTests

build-northbound:
	mvn install -pl northbound -am -DskipTests

build-messaging:
	mvn install -pl messaging -am -DskipTests

clean:
	mvn clean
	$(MAKE) -C projectfloodlight clean

.PHONY: default projectfloodlight build clean
