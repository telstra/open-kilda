# 'make' will build the latest and try to run it.
default: build-latest run-dev

build-base:
	base/hacks/storm.requirements.download.sh
	docker build -t kilda/base-ubuntu:latest base/kilda-base-ubuntu/
	docker build -t kilda/zookeeper:latest services/zookeeper
	docker build -t kilda/kafka:latest services/kafka
	docker build -t kilda/hbase:latest services/hbase
	docker build -t kilda/storm:latest services/storm
	docker build -t kilda/neo4j:latest services/neo4j
	docker build -t kilda/opentsdb:latest services/opentsdb
	docker build -t kilda/logstash:latest services/logstash
	docker build -t kilda/python3-ubuntu:latest base/kilda-base-python3/

build-latest: update-props build-base compile
	docker-compose build

run-dev:
	docker-compose up

up-test-mode:
	@echo ~~
	@echo ~~ Starting KILDA, and will print the status of Storm Topology deployments
	@echo ~~ Once the topology deployments are done, it should be safe to test
	@echo ~~
	@echo
	OK_TESTS="DISABLE_LOGIN" docker-compose up -d
	docker-compose logs -f wfm
	$(MAKE) -C tools/elk-dashboards

up-log-mode: up-test-mode
	docker-compose logs -f

# keeping run-test for backwards compatibility (documentation) .. should deprecate
run-test: up-log-mode

clean-sources:
	$(MAKE) -C services/src clean
	$(MAKE) -C services/mininet clean
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

compile:
	$(MAKE) -C services/src
	$(MAKE) -C services/wfm all-in-one
	$(MAKE) -C services/mininet
	$(MAKE) -C services/lab-service/lab test

.PHONY: unit unit-java-common unit-java-storm unit-py-te
unit: update-props unit-java-common unit-java-storm unit-py-te
unit-java-common: build-base
	$(MAKE) -C services/src
unit-java-storm: avoid-port-conflicts
	mvn -B -f services/wfm/pom.xml test
unit-py-te:
	$(MAKE) -C services/topology-engine ARTIFACTS=../../artifact/topology-engine --keep-going test test-artifacts

.PHONY: avoid-port-conflicts
avoid-port-conflicts:
	docker-compose stop

clean-test:
	docker-compose down
	docker-compose rm -fv
	docker volume list -q | grep kilda | xargs -r docker volume  rm

clean: clean-sources clean-test

update-props:
	confd -onetime -confdir ./confd/ -backend file -file ./confd/vars/main.yaml -sync-only

update-props-dryrun:
	confd -onetime -confdir ./confd/ -backend file -file ./confd/vars/main.yaml -sync-only -noop

 .PHONY: default run-dev build-latest build-base	
 .PHONY: up-test-mode up-log-mode run-test clean-test	
 .PHONY: clean-sources unit update	
 .PHONY: clean

