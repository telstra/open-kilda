#
KILDA_MAVEN := ~/.m2/repository/org/bitbucket/openkilda

default: build

projectfloodlight:
	$(MAKE) -C projectfloodlight

build: projectfloodlight
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
	rm -rf $(KILDA_MAVEN)

.PHONY: default projectfloodlight build clean
