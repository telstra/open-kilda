# 'make' will build the latest and try to run it.
default: build-latest run-dev

build-base:
	docker build -t kilda/base-ubuntu:latest base/kilda-base-ubuntu/
	docker build -t kilda/base-floodlight:latest base/base-floodlight/
	docker build -f services/zookeeper/Dockerfile -t kilda/zookeeper:latest .
	docker build -f services/kafka/Dockerfile -t kilda/kafka:latest .
	docker build -f services/hbase/Dockerfile -t kilda/hbase:latest .
	docker build -f services/mininet/Dockerfile -t kilda/mininet:latest .
	docker build -f services/neo4j/Dockerfile -t kilda/neo4j:latest .
	docker build -f services/storm/Dockerfile -t kilda/storm:latest .
	docker build -f services/opentsdb/Dockerfile -t kilda/opentsdb:latest .

build-latest: build-base
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
	mvn -f services/src/floodlight-modules/pom.xml clean
	mvn -f services/src/northbound/pom.xml clean
	mvn -f services/src/messaging/pom.xml clean
	mvn -f services/wfm/pom.xml clean

unit:
	$(MAKE) -C services/src/projectfloodlight
	mvn -f services/src/messaging/pom.xml clean install
	mvn -f services/src/floodlight-modules/pom.xml clean test
	mvn -f services/src/northbound/pom.xml clean test
	mvn -f services/wfm/pom.xml clean test

clean-test:
	docker-compose down
	docker-compose rm -fv
	docker volume list -q | grep kilda | xargs docker volume rm

# NB: To override the default (localhost) kilda location, you can make a call like this:
#		cd services/src/atdd && \
#		mvn "-Dtest=org.bitbucket.openkilda.atdd.*" \
#			-DargLine="-Dkilda.host=127.0.0.1" \
#			test
atdd:
	cd services/src/atdd && mvn "-Dtest=org.bitbucket.openkilda.atdd.*" test

smoke:
	cd services/src/atdd && mvn "-Dtest=org.bitbucket.openkilda.smoke.*" test

perf:
	cd services/src/atdd && mvn "-Dtest=org.bitbucket.openkilda.perf.*" test

sec:
	cd services/src/atdd && mvn "-Dtest=org.bitbucket.openkilda.sec.*" test

FLOODLIGHT_JAR := ~/.m2/repository/org/projectfloodlight/floodlight/1.2-SNAPSHOT/floodlight-1.2-SNAPSHOT.jar
FM_JAR := services/src/floodlight-modules/target/floodlight-modules.jar
MSG_JAR := ~/.m2/repository/org/bitbucket/openkilda/messaging/1.0-SNAPSHOT/messaging-1.0-SNAPSHOT.jar

$(MSG_JAR):
	mvn -f services/src/messaging/pom.xml install

$(FM_JAR): $(MSG_JAR)
	$(MAKE) -C services/src/projectfloodlight
	mvn -f services/src/floodlight-modules/pom.xml package

build-floodlight: $(FM_JAR)

clean-floodlight:
	rm -rf ~/.m2/repository/org/bitbucket/openkilda/messaging/
	mvn -f services/src/messaging/pom.xml clean
	mvn -f services/src/floodlight-modules/pom.xml clean
	$(MAKE) -C services/src/projectfloodlight clean

run-floodlight: build-floodlight
	java -Dlogback.configurationFile=services/src/floodlight-modules/src/test/resources/logback.xml \
	-cp $(FLOODLIGHT_JAR):$(FM_JAR) net.floodlightcontroller.core.Main \
	-cf services/src/floodlight-modules/src/main/resources/floodlightkilda.properties

.PHONY: default run-dev build-latest build-base
.PHONY: up-test-mode up-log-mode run-test clean-test
.PHONY: smoke acceptance perf sec unit
.PHONY: build-floodlight clean-floodlight run-floodlight
