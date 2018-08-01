#
# This Makefile holds useful commands for developing and testing the Flow CRUD use cases.
#
# Key elements of this are:
#   - Launch just enough of the Kilda platform to be able to deploy storm topologies
#       (e.g. mininet, floodlight, storm, neo4j, TE)
#   - Deploy the storm topologies related to Flow CRUD
#
# Typical sequence of calls to validate behavior (from root of project):
#   1) make -f base/hacks/usecase/flow.crud.make up
#   2) make -f base/hacks/usecase/flow.crud.make start
#   3) make -f base/hacks/usecase/flow.crud.make deploy-flow-topo
#   4) make -f base/hacks/usecase/flow.crud.make deploy-flows
#   5) make -f base/hacks/usecase/flow.crud.make get-flows
#   6) make -f base/hacks/usecase/flow.crud.make validate-flows
#   7) make -f base/hacks/usecase/flow.crud.make clean-flows
#   *) make -f base/hacks/usecase/flow.crud.make dump.flow
#   *) make -f base/hacks/usecase/flow.crud.make dump.topo.disco
#   *) make -f base/hacks/usecase/flow.crud.make dump.speaker
#   *) make -f base/hacks/usecase/flow.crud.make dump.topo.eng
#
# Additionally, goto http://localhost:7474/browser/ to verify results in neo4j
#


help:
	@echo ""
	@echo "This is the Makefile for Flow CRUD development and test commands."
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
.PHONY: start stop login-storm login-mininet

start:
	@echo ""
	@echo "NB: This requires storm 1.1.0 or great; return `storm version` to verify."
	cd services/wfm && ${MAKE} deploy-wfm deploy-flow

stop:
	cd services/wfm && ${MAKE} kill-flow kill-wfm

login-storm:
	@echo ""
	@echo "NB: For Logs, look at /opt/storm/logs/[workers-artifacts]"
	docker-compose exec storm-supervisor "/bin/bash"

login-mininet:
	@echo ""
	@echo "NB: For examples, look at /app/examples or /usr/share/doc/mininet/examples"
	docker-compose exec mininet "/bin/bash"



## =~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=
## Lifecycle Network Topologies
##
## - You can verify the sequence of events through inspection of the right kafka queues.
## - It starts with creating a topology in mininet
## =~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=
.PHONY: deploy-flow-topo clean-topo deploy-flows clean-flows get-flows validate-flows

deploy-flow-topo:
	@echo ""
	@echo "==> Cleans the topology, then creates a small topology."
	@echo "==> Look at services/topology-engine/queue-engine/tests/smoke-tests/create-flow-topology.py"
	cd services/topology-engine/queue-engine/tests/smoke-tests && ./create-flow-topology.py

clean-topo:
	@echo ""
	services/topology-engine/queue-engine/tests/smoke-tests/clean-topology.py

deploy-flows:
	@echo ""
	cd services/topology-engine/queue-engine/tests/smoke-tests && ./deploy-flow-rules.py


clean-flows:
	@echo ""
	cd services/topology-engine/queue-engine/tests/smoke-tests && ./clean-flow-rules.py

get-flows:
	@echo ""
	cd services/topology-engine/queue-engine/tests/smoke-tests && ./get-flow-rules.py

validate-flows:
	@echo ""
	cd base/hacks/usecase && ./validate_flows.sh


## =~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=
## Dump Queues
##
## - kilda.topo.disco
## - kilda.topo.eng
## - kilda.speaker
## =~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=
.PHONY: dump.topo.disco dump.topo.eng dump.speaker list.topics

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
