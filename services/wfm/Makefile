KAFKA_BIN ?= /usr/local/Cellar/kafka/0.10.2.0/bin

start-k: start-z
	${KAFKA_BIN}/kafka-server-start /usr/local/etc/kafka/server.properties &

stop-k:
	${KAFKA_BIN}/kafka-server-stop /usr/local/etc/kafka/server.properties

start-z:
	zkServer start

stop-z:
	zkServer stop
