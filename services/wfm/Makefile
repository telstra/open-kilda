#
# NB: On the mac, brew currently installs 0.11.0.1. (as of 2017.12.24).
#     To get 0.10.2.1, look at https://www.apache.org/dyn/closer.cgi?path=/kafka/0.10.2.1/kafka_2.12-0.10.2.1.tgz
#
KAFKA_BIN ?= /usr/local/Cellar/kafka/0.11.0.1/bin

start-k: start-z
	${KAFKA_BIN}/kafka-server-start /usr/local/etc/kafka/server.properties &

stop-k:
	${KAFKA_BIN}/kafka-server-stop /usr/local/etc/kafka/server.properties

start-z:
	zkServer start

stop-z:
	zkServer stop

all-in-one-tested:
	mvn assembly:assembly

all-in-one:
	mvn assembly:assembly -DskipTests

deploy-wfm:
	storm jar target/WorkflowManager-1.0-SNAPSHOT-jar-with-dependencies.jar \
        org.openkilda.wfm.topology.event.OFEventWFMTopology \
        --name=wfm ${config}

kill-wfm:
	storm kill wfm >/dev/null 2>&1

deploy-flow:
	storm jar target/WorkflowManager-1.0-SNAPSHOT-jar-with-dependencies.jar \
        org.openkilda.wfm.topology.flow.FlowTopology \
        --name=flow ${config}

kill-flow:
	storm kill flow >/dev/null 2>&1

deploy-stats:
	storm jar target/WorkflowManager-1.0-SNAPSHOT-jar-with-dependencies.jar \
        org.openkilda.wfm.topology.stats.StatsTopology \
        --name=stats ${config}

kill-stats:
	storm kill stats >/dev/null 2>&1

deploy-cache:
	storm jar target/WorkflowManager-1.0-SNAPSHOT-jar-with-dependencies.jar \
        org.openkilda.wfm.topology.cache.CacheTopology \
        --name=cache ${config}

kill-cache:
	storm kill cache >/dev/null 2>&1

deploy-islstats:
	storm jar target/WorkflowManager-1.0-SNAPSHOT-jar-with-dependencies.jar \
		org.openkilda.wfm.topology.islstats.IslStatsTopology \
		--name=islstats ${config}

kill-islstats:
	storm kill islstats >/dev/null 2>&1

deploy-opentsdb:
	storm jar target/WorkflowManager-1.0-SNAPSHOT-jar-with-dependencies.jar \
		org.openkilda.wfm.topology.opentsdb.OpenTSDBTopology \
		--name=opentsdb ${config}

kill-opentsdb:
	storm kill opentsdb >/dev/null 2>&1

deploy-portstate:
	storm jar target/WorkflowManager-1.0-SNAPSHOT-jar-with-dependencies.jar \
		org.openkilda.wfm.topology.portstate.PortStateTopology \
		--name=portstate ${config}

kill-portstate:
	storm kill portstate >/dev/null 2>&1

deploy-nbworker:
	storm jar target/WorkflowManager-1.0-SNAPSHOT-jar-with-dependencies.jar \
		org.openkilda.wfm.topology.nbworker.NbWorkerTopology \
		--name=nbworker ${config}

kill-nbworker:
	storm kill nbworker >/dev/null 2>&1

