#
# NB: On the mac, brew currently installs 0.10.2.0. (as of 2017.06.10).
#     To get 0.10.2.1, look at https://www.apache.org/dyn/closer.cgi?path=/kafka/0.10.2.1/kafka_2.12-0.10.2.1.tgz
#
KAFKA_BIN ?= /usr/local/Cellar/kafka/0.10.2.1/bin

start-k: start-z
	${KAFKA_BIN}/kafka-server-start.sh /usr/local/etc/kafka/server.properties &

stop-k:
	${KAFKA_BIN}/kafka-server-stop.sh /usr/local/etc/kafka/server.properties

start-z:
	zkServer start

stop-z:
	zkServer stop

all-in-one-tested:
	mvn assembly:assembly

all-in-one:
	mvn assembly:assembly -DskipTests

deploy-splitter:
	storm jar target/WorkflowManager-1.0-SNAPSHOT-jar-with-dependencies.jar \
        org.openkilda.wfm.topology.splitter.OFEventSplitterTopology \
        splitter ${config}

kill-splitter:
	storm kill splitter >/dev/null 2>&1

deploy-wfm:
	storm jar target/WorkflowManager-1.0-SNAPSHOT-jar-with-dependencies.jar \
        org.openkilda.wfm.topology.event.OFEventWFMTopology \
        wfm ${config}

kill-wfm:
	storm kill wfm >/dev/null 2>&1

deploy-flow:
	storm jar target/WorkflowManager-1.0-SNAPSHOT-jar-with-dependencies.jar \
        org.openkilda.wfm.topology.flow.FlowTopology \
        flow ${config}

kill-flow:
	storm kill flow >/dev/null 2>&1

deploy-stats:
	storm jar target/WorkflowManager-1.0-SNAPSHOT-jar-with-dependencies.jar \
        org.openkilda.wfm.topology.stats.StatsTopology \
        stats ${config}

kill-stats:
	storm kill stats >/dev/null 2>&1

deploy-cache:
	storm jar target/WorkflowManager-1.0-SNAPSHOT-jar-with-dependencies.jar \
        org.openkilda.wfm.topology.cache.CacheTopology \
        cache ${config}

kill-cache:
	storm kill cache >/dev/null 2>&1

deploy-islstats:
	storm jar target/WorkflowManager-1.0-SNAPSHOT-jar-with-dependencies.jar \
		org.bitbucket.openkilda.wfm.topology.islstats.IslStatsTopology \
		islstats ${config}

kill-islstats:
	storm kill islstats >/dev/null 2>&1

create-topics:
	src/main/resources/create-channels.sh
