KAFKA_BIN ?= /usr/local/Cellar/kafka/0.10.2.0/bin

start-k: start-z
	${KAFKA_BIN}/kafka-server-start /usr/local/etc/kafka/server.properties &

stop-k:
	${KAFKA_BIN}/kafka-server-stop /usr/local/etc/kafka/server.properties

start-z:
	zkServer start

stop-z:
	zkServer stop

all-in-one:
	mvn assembly:assembly -DskipTests

deploy-splitter:
	storm jar target/WorkflowManager-1.0-SNAPSHOT-jar-with-dependencies.jar \
        org.bitbucket.openkilda.wfm.topology.event.OFEventSplitterTopology \
        splitter

kill-splitter:
	storm kill splitter >/dev/null 2>&1

deploy-wfm:
	storm jar target/WorkflowManager-1.0-SNAPSHOT-jar-with-dependencies.jar \
        org.bitbucket.openkilda.wfm.topology.event.OFEventWFMTopology \
        wfm

kill-wfm:
	storm kill wfm >/dev/null 2>&1

deploy-flow:
	storm jar target/WorkflowManager-1.0-SNAPSHOT-jar-with-dependencies.jar \
        org.bitbucket.openkilda.wfm.topology.flow.FlowTopology \
        flow

kill-flow:
	storm kill flow >/dev/null 2>&1

create-topics:
	src/main/resources/create-channels.sh
