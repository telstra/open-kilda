# WFM topology management

TOPOLOGY := network flowhs stats isllatency opentsdb portstate nbworker ping reroute swmanager floodlightrouter connecteddevices server42-control

TOPOLOGY_JAR := target/WorkflowManager-1.0-SNAPSHOT-jar-with-dependencies.jar
TOPOLOGY_CONFIG := topology.properties

STORM := /opt/storm/bin/storm

status_location := topology-state

deploy_targets := $(addprefix deploy-,$(TOPOLOGY))
kill_targets := $(addprefix kill-,$(TOPOLOGY))

deploy_topology_name = $(patsubst deploy-%,%,$@)
kill_topology_name = $(patsubst kill-%,%,$@)

deploy-all: alive-list $(addsuffix .alive,$(addprefix $(status_location)/$(prefix),$(TOPOLOGY)))
kill-all: $(kill_targets)


$(status_location)/$(prefix)%.alive:
	$(MAKE) deploy-$(patsubst $(status_location)/$(prefix)%.alive,%,$@)
	touch $@

$(deploy_targets):
	./deploy_single_topology.sh $(deploy_topology_name) ${TOPOLOGY_CONFIG} $(prefix)

$(kill_targets): alive-list
	if [ -e $(status_location)/$(prefix)$(kill_topology_name).alive ]; then ./kill-topology.sh $(prefix)$(kill_topology_name); fi

alive-list:
	mkdir -p $(status_location)
	rm -f $(status_location)/*.alive
	for t in $$($(STORM) list | sed -n '/^-----/,$$ {s: \+.*$$::; p}' | tail -n+2); do touch $(status_location)/$$t.alive; done

debug-logging-%: alive-list $(status_location)/$(prefix)%.alive
	./enable-debug-logging.sh $*

.PHONY: deploy-all kill-all alive-list
.PHONY: $(deploy_targets) $(kill_targets)
