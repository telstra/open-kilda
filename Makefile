# 'make' will build docker images and try to run it.
default: update-props build-stable up-test-mode

UPDATE_PROPS := confd -onetime -confdir ./confd/ -backend file -file ./confd/vars/main.yaml -file ./confd/vars/docker-compose.yaml -sync-only

generated.mk update-props update-props-blue:
	$(UPDATE_PROPS) -file ./confd/vars/blue-mode.yaml

update-props-green:
	$(UPDATE_PROPS) -file ./confd/vars/green-mode.yaml

update-props-dryrun:
	$(UPDATE_PROPS) -noop

.PHONY: default update-props update-props-blue update-props-green update-props-dryrun

include generated.mk
