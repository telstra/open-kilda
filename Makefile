# 'make' will build the latest and try to run it.
default: build-latest run-dev

build-base:
	docker build -t kilda/base-ubuntu:latest base/kilda-base-ubuntu/
	docker build -t kilda/base-floodlight:latest base/base-floodlight/

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

clean-test:
	docker-compose down
	docker-compose rm -fv
	docker volume list -q | grep kilda | xargs docker volume rm

# NB: To override the default (localhost) kilda location, you can make a call like this:
#		cd src/atdd && \
#		mvn "-Dtest=org.bitbucket.openkilda.atdd.*" \
#			-DargLine="-Dkilda.host=127.0.0.1" \
#			test
atdd:
	cd src/atdd && mvn "-Dtest=org.bitbucket.openkilda.atdd.*" test

smoke:
	cd src/atdd && mvn "-Dtest=org.bitbucket.openkilda.smoke.*" test

perf:
	cd src/atdd && mvn "-Dtest=org.bitbucket.openkilda.perf.*" test

sec:
	cd src/atdd && mvn "-Dtest=org.bitbucket.openkilda.sec.*" test

.PHONY: default run-dev build-latest build-base
.PHONY: up-test-mode up-log-mode run-test clean-test
.PHONY: smoke acceptance perf sec
