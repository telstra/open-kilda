# 'make' will build the latest and try to run it.
default: build-latest run-dev

java_version := "1.8"

check-java-version:
	if  [ `java -version 2>&1 | awk -F '"' '/version/ { print $$2 }' | awk -F'.' '{ print $$1"."$$2 }'` != "$(java_version)" ]; then false; fi

build-base: update-props check-java-version

build-base: update-props
	docker/base/hacks/storm.requirements.download.sh
	docker build -t kilda/base-ubuntu:latest docker/base/kilda-base-ubuntu/
	docker build -t kilda/zookeeper:latest docker/zookeeper
	docker build -t kilda/kafka:latest docker/kafka
	docker build -t kilda/hbase:latest docker/hbase
	docker build -t kilda/storm:latest docker/storm
	docker build -t kilda/neo4j:latest docker/neo4j
	docker build -t kilda/opentsdb:latest docker/opentsdb
	docker build -t kilda/logstash:latest docker/logstash
	docker build -t kilda/base-lab-service:latest docker/base/kilda-base-lab-service/

build-latest: build-base compile
	docker-compose build

run-dev:
	docker-compose up

up-test-mode:
	@echo ~~
	@echo ~~ Starting KILDA, and will print the status of Storm Topology deployments
	@echo ~~ Once the topology deployments are done, it should be safe to test
	@echo ~~
	@echo
	cp -n .env.example .env
	OK_TESTS="DISABLE_LOGIN" docker-compose up -d
#	docker-compose logs -f wfm
	$(MAKE) -C tools/elk-dashboards

up-log-mode: up-test-mode
	docker-compose logs -f

# keeping run-test for backwards compatibility (documentation) .. should deprecate
run-test: up-log-mode

clean-sources:
	$(MAKE) -C services/src/openkilda-gui clean-java
	$(MAKE) -C src-python/lab-service/lab clean
	cd src-java && ./gradlew clean

compile: update-props
	cd src-java && ./gradlew build --stacktrace
	$(MAKE) -C src-python/lab-service/lab test
	$(MAKE) -C services/src/openkilda-gui build

.PHONY: unit
unit: update-props
	cd src-java && ./gradlew test --stacktrace

.PHONY: sonar
sonar: update-props
	cd src-java && ./gradlew test jacocoTestReport sonarqube --stacktrace

clean-test:
	docker-compose down
	docker-compose rm -fv
	docker volume list -q | grep kilda | xargs -r docker volume  rm

clean: clean-sources clean-test

update-props:
	confd -onetime -confdir ./confd/ -backend file -file ./confd/vars/main.yaml -sync-only

update-props-dryrun:
	confd -onetime -confdir ./confd/ -backend file -file ./confd/vars/main.yaml -sync-only -noop

# EXAMPLES:
#   make func-tests  // run all tests
#   make func-tests PARAMS='-Dtest=spec.links.**'  // run all tests from 'spec.links' package
#   make func-tests PARAMS='-Dtest=LinkSpec'  // run all tests from 'LinkSpec' class
#   make func-tests PARAMS='-Dtest="LinkSpec#Able to delete inactive link"'  // run a certain test from 'LinkSpec' class

func-tests:
	cp src-java/testing/functional-tests/kilda.properties.example src-java/testing/functional-tests/kilda.properties
	cp src-java/testing/functional-tests/src/test/resources/topology.yaml src-java/testing/functional-tests/topology.yaml
	cd src-java && ./gradlew :functional-tests:functionalTest --stacktrace $(PARAMS)

.PHONY: default run-dev build-latest build-base	
.PHONY: up-test-mode up-log-mode run-test clean-test	
.PHONY: clean-sources unit update	
.PHONY: clean
.PHONY: func-tests
