# WFM topology management

BLUE_GREEN_CONFIG = blue_green.config
-include ${BLUE_GREEN_CONFIG}

TOPOLOGY := network flowhs stats isllatency opentsdb portstate nbworker ping reroute swmanager floodlightrouter connecteddevices server42-control

TOPOLOGY_JAR := target/WorkflowManager-1.0-SNAPSHOT-jar-with-dependencies.jar
TOPOLOGY_CONFIG := topology.properties

STORM := /opt/storm/bin/storm

status_location := topology-state

deploy_targets := $(addprefix deploy-,$(TOPOLOGY) $(addsuffix -blue,$(TOPOLOGY)) $(addsuffix -green,$(TOPOLOGY)))
kill_targets := $(addprefix kill-,$(TOPOLOGY) $(addsuffix -blue,$(TOPOLOGY)) $(addsuffix -green,$(TOPOLOGY)))

deploy_topology_name = $(patsubst %-green,%,$(patsubst %-blue,%,$(patsubst deploy-%,%,$@)))
kill_topology_name = $(patsubst %-green,%,$(patsubst %-blue,%,$(patsubst kill-%,%,$@)))
deploy_blue_green_topology_name = $(@:deploy-blue-green-%=%)
deployment_mode = $(lastword $(subst -, _,$(filter %-green %-blue, $@)))

deploy-all: alive-list $(addprefix deploy-blue-green-, $(TOPOLOGY))
kill-all: $(kill_targets)

deploy-blue-green-%:
	if [ "$(blue_green_$(deploy_blue_green_topology_name))" = "True" ]; then \
		$(MAKE) $(status_location)/$(prefix)$(deploy_blue_green_topology_name)-blue.alive; \
		$(MAKE) $(status_location)/$(prefix)$(deploy_blue_green_topology_name)-green.alive; \
	else \
		$(MAKE) $(status_location)/$(prefix)$(deploy_blue_green_topology_name).alive; \
	fi

$(status_location)/$(prefix)%.alive:
	$(MAKE) deploy-$(patsubst $(status_location)/$(prefix)%.alive,%,$@)
	touch $@

$(deploy_targets):
	./deploy_single_topology.sh "$(deploy_topology_name)" "${TOPOLOGY_CONFIG}" "$(prefix)" "$(deployment_mode)"

$(kill_targets): alive-list
	if [ -e $(status_location)/$(prefix)$(kill_topology_name)$(deployment_mode).alive ]; \
	then ./kill-topology.sh $(prefix)$(kill_topology_name)$(deployment_mode); fi

alive-list:
	mkdir -p $(status_location)
	rm -f $(status_location)/*.alive
	for t in $$($(STORM) list | sed -n '/^-----/,$$ {s: \+.*$$::; p}' | tail -n+2); do touch $(status_location)/$$t.alive; done

debug-logging-%: alive-list $(status_location)/$(prefix)%.alive
	./enable-debug-logging.sh $*

.PHONY: deploy-all kill-all alive-list
.PHONY: $(deploy_targets) $(kill_targets)
