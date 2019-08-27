# 'make' will build the latest and try to run it.
default: build-latest run-dev

build-base: update-props
	base/hacks/storm.requirements.download.sh
	docker build -t kilda/base-ubuntu:latest base/kilda-base-ubuntu/
	docker build -t kilda/zookeeper:latest services/zookeeper
	docker build -t kilda/kafka:latest services/kafka
	docker build -t kilda/hbase:latest services/hbase
	docker build -t kilda/storm:latest services/storm
	docker build -t kilda/neo4j:latest services/neo4j
	docker build -t kilda/opentsdb:latest services/opentsdb
	docker build -t kilda/logstash:latest services/logstash
	docker build -t kilda/base-lab-service:latest base/kilda-base-lab-service/

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
	OK_TESTS="DISABLE_LOGIN" docker-compose -f docker-compose.yml -f docker-compose.test.yml up -d
	docker-compose logs -f wfm
	$(MAKE) -C tools/elk-dashboards

up-log-mode: up-test-mode
	docker-compose logs -f

# keeping run-test for backwards compatibility (documentation) .. should deprecate
run-test: up-log-mode

clean-sources:
	$(MAKE) -C services/src clean
	$(MAKE) -C services/lab-service/lab clean
	mvn -f services/wfm/pom.xml clean

update-parent:
	mvn --non-recursive -f services/src/pom.xml install -DskipTests

update-core:
	mvn -f services/src/kilda-core/pom.xml install -DskipTests

update-pce:
	mvn -f services/src/kilda-pce/pom.xml install -DskipTests

update-msg:
	mvn -f services/src/messaging/pom.xml install -DskipTests

update: update-parent update-core update-msg update-pce

compile: update-props
	$(MAKE) -C services/src
	$(MAKE) -C services/wfm all-in-one
	$(MAKE) -C services/lab-service/lab test

.PHONY: unit
unit: update-props
	$(MAKE) build-no-test -C services/src
	$(MAKE) unit -C services/src
	mvn -B -f services/wfm/pom.xml test

clean-test:
	docker-compose -f docker-compose.yml -f docker-compose.test.yml down
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
	cp services/src/functional-tests/kilda.properties.example services/src/functional-tests/kilda.properties
	cp services/src/functional-tests/src/test/resources/topology.yaml services/src/functional-tests/topology.yaml
	mvn -Pfunctional -f services/src/functional-tests/pom.xml test $(PARAMS)

.PHONY: default run-dev build-latest build-base	
.PHONY: up-test-mode up-log-mode run-test clean-test	
.PHONY: clean-sources unit update	
.PHONY: clean
.PHONY: func-tests
