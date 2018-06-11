# 'make' will build the latest and try to run it.
default: build-latest run-dev

build-base:
	base/hacks/kilda-bins.download.sh
	base/hacks/shorm.requirements.download.sh
	rsync -au kilda-bins/zookeeper* services/zookeeper/tar/
	rsync -au kilda-bins/hbase* services/hbase/tar/
	rsync -au kilda-bins/kafka* services/kafka/tar/
	rsync -au kilda-bins/apache-storm* services/storm/tar/
	docker build -t kilda/base-ubuntu:latest base/kilda-base-ubuntu/
	docker build -t kilda/zookeeper:latest services/zookeeper
	docker build -t kilda/kafka:latest services/kafka
	docker build -t kilda/hbase:latest services/hbase
	docker build -t kilda/storm:latest services/storm
	docker build -t kilda/neo4j:latest services/neo4j
	docker build -t kilda/opentsdb:latest services/opentsdb
	docker build -t kilda/logstash:latest services/logstash

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
	OK_TESTS="DISABLE_LOGIN" docker-compose up -d
	docker-compose logs -f wfm

up-log-mode: up-test-mode
	docker-compose logs -f

# keeping run-test for backwards compatibility (documentation) .. should depracate
run-test: up-log-mode

clean-sources:
	$(MAKE) -C services/src clean
	$(MAKE) -C services/mininet clean
	mvn -f services/wfm/pom.xml clean

update-parent:
	mvn --non-recursive -f services/src/pom.xml install -DskipTests

update-pce:
	mvn -f services/src/pce/pom.xml install -DskipTests

update-msg:
	mvn -f services/src/messaging/pom.xml install -DskipTests

update: update-parent update-msg update-pce


compile:
	$(MAKE) -C services/src
	$(MAKE) -C services/wfm all-in-one
	$(MAKE) -C services/mininet

.PHONY: unit unit-java-common unit-java-storm unit-py-te
unit: unit-java-common unit-java-storm unit-py-te
unit-java-common: build-base
	$(MAKE) -C services/src
unit-java-storm: avoid-port-conflicts
	mvn -B -f services/wfm/pom.xml test
unit-py-te:
	$(MAKE) -C services/topology-engine ARTIFACTS=../../artifact/topology-engine test

.PHONY: avoid-port-conflicts
avoid-port-conflicts:
	docker-compose stop

clean-test:
	docker-compose down
	docker-compose rm -fv
	docker volume list -q | grep kilda | xargs docker volume rm

update-props:
	ansible-playbook -D -s config.yml

update-props-dryrun:
	ansible-playbook -D -C -v -s config.yml

# NB: To override the default (localhost) kilda location, you can make a call like this:
#		cd services/src/atdd && \
#		mvn "-Dtest=org.bitbucket.openkilda.atdd.*" \
#			-DargLine="-Dkilda.host=127.0.0.1" \
#			test

#
# NB: Adjust the default tags as ATDD tests are created and validated.
# 		Regarding syntax .. @A,@B is logical OR .. --tags @A --tags @B is logical AND
#
# (crimi) - 2018.03.25 .. these tags seem to be the right tags for ATDD
# tags := @TOPO,@FCRUD,@NB,@FPATH,@FREINSTALL,@PCE --tags @MVP1
#
tags := @TOPO,@FCRUD,@FREINSTALL --tags @MVP1
kilda := 127.0.0.1

# EXAMPLES:
#  ( @NB OR @STATS ) AND @MVP1
#   --tags @NB,@STATS --tags @MVP1
#   make atdd kilda=127.0.0.1 tags=@
#   mvn -f services/src/atdd/pom.xml -Patdd test -Dkilda.host="127.0.0.1" -Dcucumber.options="--tags @CRUD_UPDATE"
#   mvn -f services/src/atdd/pom.xml -Patdd test -Dkilda.host="127.0.0.1" -Dsurefire.useFile=false -Dcucumber.options="--tags @CRUD_UPDATE"

atdd: update
	mvn -f services/src/atdd/pom.xml -P$@ test -Dkilda.host="$(kilda)" -Dcucumber.options="--tags $(tags)"

smoke: update
	mvn -f services/src/atdd/pom.xml -P$@ test -Dkilda.host="$(kilda)"

perf: update
	mvn -f services/src/atdd/pom.xml -P$@ test -Dkilda.host="$(kilda)"

sec: update
	mvn -f services/src/atdd/pom.xml -P$@ test -Dkilda.host="$(kilda)"

.PHONY: default run-dev build-latest build-base
.PHONY: up-test-mode up-log-mode run-test clean-test
.PHONY: atdd smoke perf sec
.PHONY: clean-sources unit update
