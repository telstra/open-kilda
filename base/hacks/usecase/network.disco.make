#
# This Makefile holds useful commands for developing and testing the Network Discovery use cases.
#
# Key elements of this are:
#   - Launch just enough of the Kilda platform to be able to deploy storm topologies
#       (e.g. mininet, floodlight, storm, neo4j, TE)
#   - Deploy the storm topologies related to Network Discovery (e.g OFEventWFMTopology)
#
# Typical sequence of calls to validate behavior (from root of project):
#   1) make -f base/hacks/usecase/network.disco.make up
#   2) make -f base/hacks/usecase/network.disco.make start
#   3) make -f base/hacks/usecase/network.disco.make deploy-small
#   4) make -f base/hacks/usecase/network.disco.make dump.topo.disco
#   5) make -f base/hacks/usecase/network.disco.make dump.speaker
#   6) make -f base/hacks/usecase/network.disco.make dump.topo.eng
#   7) make atdd tags="@TOPO --tags @SMOKE"   # This is done after, to get a sanity check
#
# Additionally, goto http://localhost:7474/browser/ to verify results in neo4j
#


help:
	@echo ""
	@echo "This is the Makefile for Network Discovery development and test commands."
	@echo "Useful targets are:"
	@echo "  help  : prints this output"
	@echo "  up    : brings up the containers, but not the storm topologies"
	@echo "  down  : brings down the containers"
	@echo "  clean : destroys the volume data; calls platform.down first"
	@echo "  start : deploy the storm topology"
	@echo "  deploy-small    : create a small network topology"
	@echo "  deploy-tiny     : create a tiny network topology (3 switches, full mesh)"
	@echo "  dump.topo.disco : dump the kafka topic between speaker and storm"
	@echo "  dump.topo.eng   : dump the kafka topic between storm and topology engine"
	@echo "  list.topics     : list all the topics known to kafka"
	@echo ""
	@echo "NB: This makefile assumes it is running from the the top level directory of the project"
	@echo "  - e.g. make -f base/hacks/usecase/network.disco.make help"
.PHONY: help

## =~=~=~=~=~=~=~=~=~=
## Platform commands
## =~=~=~=~=~=~=~=~=~=
.PHONY: up down clean

up:
	# NB: just specifying the minimal set .. taking advantage of embedded dependencies to bring
	#     up the remaining containers
	OK_TESTS="DISABLE_LOGIN" docker-compose up -d mininet floodlight storm-supervisor \
	topology-engine kibana

down:
	docker-compose down

clean:
	${MAKE} clean-test


## =~=~=~=~=~=~=~=~=~=~=~=~=~=
## Lifecycle Storm Topologies
## =~=~=~=~=~=~=~=~=~=~=~=~=~=
.PHONY: start stop login

start:
	@echo ""
	@echo "NB: This requires storm 1.1.0 or great; return `storm version` to verify."
	cd services/wfm && ${MAKE} deploy-wfm

stop:
	cd services/wfm && ${MAKE} kill-wfm

login:
	@echo ""
	@echo "NB: For Logs, look at /opt/storm/logs/[workers-artifacts]"
	docker-compose exec storm-supervisor "/bin/bash"

## =~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=
## Lifecycle Network Topologies
##
## - You can verify the sequence of events through inspection of the right kafka queues.
## - It starts with creating a topology in mininet
## =~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=
.PHONY: deploy-small deploy-tiny clean-network

deploy-small:
	@echo ""
	@echo "==> Cleans the topology, then creates a small topology."
	@echo "==> Look at services/topology-engine/queue-engine/tests/smoke-tests/create-small-topology.py"
	cd services/topology-engine/queue-engine/tests/smoke-tests && ./create-small-topology.py

deploy-tiny:
	@echo ""
	@echo "==> Cleans the topology, then creates a tiny topology."
	@echo "==> Look at services/topology-engine/queue-engine/tests/smoke-tests/create-tinyu-topology.py"
	cd services/topology-engine/queue-engine/tests/smoke-tests && ./create-tiny-topology.py

clean-network:
	@echo ""
	services/topology-engine/queue-engine/tests/smoke-tests/clean-topology.py


## =~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=
## Dump Queues
##
## - kilda.topo.disco
## - kilda.topo.eng
## - kilda.speaker
## =~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=
.PHONY: dump.topo.disco dump.topo.eng dump.speaker dump.topics

dump.topo.disco:
	@echo ""
	@echo "==> Use this to validate traffic between the speaker and the storm topology"
	@echo ""
	kafka-console-consumer --bootstrap-server localhost:9092 --topic kilda.topo.disco --from-beginning

dump.topo.eng:
	@echo ""
	@echo "==> Use this to validate traffic between the storm topology and the topology engine"
	@echo ""
	kafka-console-consumer --bootstrap-server localhost:9092 --topic kilda.topo.eng --from-beginning

dump.speaker:
	@echo ""
	@echo "==> Use this to validate traffic between the storm topology and the speaker (ISL disco)"
	@echo ""
	kafka-console-consumer --bootstrap-server localhost:9092 --topic kilda.speaker --from-beginning

list.topics:
	@echo ""
	kafka-topics --zookeeper localhost:2181 --list
