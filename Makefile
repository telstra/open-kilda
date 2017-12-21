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
	docker build -t kilda/base-floodlight:latest base/base-floodlight/
	docker build -t kilda/zookeeper:latest services/zookeeper
	docker build -t kilda/kafka:latest services/kafka
	docker build -t kilda/hbase:latest services/hbase
	docker build -t kilda/storm:latest services/storm
	docker build -t kilda/neo4j:latest services/neo4j
	docker build -t kilda/opentsdb:latest services/opentsdb
	docker build -t kilda/mininet:latest services/mininet
	docker build -t kilda/logstash:latest services/logstash

build-latest: build-base compile
	docker-compose build

run-dev:
	docker-compose up

up-test-mode:
	OK_TESTS="DISABLE_LOGIN" docker-compose up -d

up-log-mode: up-test-mode
	docker-compose logs -f

# keeping run-test for backwards compatibility (documentation) .. should depracate
run-test: up-log-mode

clean-sources:
	$(MAKE) -C services/src clean
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

unit:
	$(MAKE) -C services/src
	mvn -f services/wfm/pom.xml clean assembly:assembly

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


kilda := 127.0.0.1
tags := "@MVP1"

# ( @NB OR @STATS ) AND @MVP1
# --tags @NB,@STATS --tags @MVP1
# make atdd kilda=127.0.0.1 tags=@MVP1

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
