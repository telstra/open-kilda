#
KILDA_MAVEN := ~/.m2/repository/org/bitbucket/openkilda

default: build

projectfloodlight:
	$(MAKE) -C projectfloodlight

build: projectfloodlight
	mvn clean install

clean:
	mvn clean
	$(MAKE) -C projectfloodlight clean
	rm -rf $(KILDA_MAVEN)

.PHONY: default projectfloodlight build clean
