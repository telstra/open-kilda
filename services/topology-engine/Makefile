# topology-engine

COMPONENT_NAME := toplogy-engine

all:
	echo "There is no default action"

test:
	echo "Perform ${COMPONENT_NAME} testing"
	docker-compose up -d --build topology-engine
	docker-compose exec topology-engine bash -c 'set -e; cd /queue-engine; python -m unittest discover --verbose -s topologylistener/tests > /tmp/tests.log'
ifdef ARTIFACTS
	mkdir -p "${ARTIFACTS}/test"
	docker cp topology-engine:/tmp/tests.log "${ARTIFACTS}/test/functional.log"
endif


.PHONY: All test
