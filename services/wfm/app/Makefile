# WFM topology management

TOPOLOGY := wfm flow stats cache islstats opentsdb portstate nbworker ping

ENTRY_wfm := org.openkilda.wfm.topology.event.OfEventWfmTopology
ENTRY_flow := org.openkilda.wfm.topology.flow.FlowTopology
ENTRY_stats := org.openkilda.wfm.topology.stats.StatsTopology
ENTRY_cache := org.openkilda.wfm.topology.cache.transport.CacheTopology
ENTRY_islstats := org.openkilda.wfm.topology.islstats.IslStatsTopology
ENTRY_opentsdb := org.openkilda.wfm.topology.opentsdb.OpenTsdbTopology
ENTRY_portstate := org.openkilda.wfm.topology.portstate.PortStateTopology
ENTRY_nbworker := org.openkilda.wfm.topology.nbworker.NbWorkerTopology
ENTRY_ping := org.openkilda.wfm.topology.ping.PingTopology

TOPOLOGY_JAR := target/WorkflowManager-1.0-SNAPSHOT-jar-with-dependencies.jar
TOPOLOGY_CONFIG := topology.properties

status_location := topology-state

deploy_targets := $(addprefix deploy-,$(TOPOLOGY))
kill_targets := $(addprefix kill-,$(TOPOLOGY))

entry_point = $(ENTRY_$(patsubst deploy-%,%,$@))
deploy_topology_name = $(patsubst deploy-%,%,$@)
kill_topology_name = $(patsubst kill-%,%,$@)

deploy-all: alive-list $(addsuffix .alive,$(addprefix $(status_location)/$(prefix),$(TOPOLOGY)))
kill-all: $(kill_targets)


$(status_location)/$(prefix)%.alive:
	$(MAKE) deploy-$(patsubst $(status_location)/$(prefix)%.alive,%,$@)
	touch $@

$(deploy_targets):
	storm jar $(TOPOLOGY_JAR) $(entry_point) --name=$(prefix)$(deploy_topology_name) ${TOPOLOGY_CONFIG}

$(kill_targets): alive-list
	if [ -e $(status_location)/$(prefix)$(kill_topology_name).alive ]; then ./kill-topology.sh $(prefix)$(kill_topology_name); fi

alive-list:
	mkdir -p $(status_location)
	rm -f $(status_location)/*.alive
	for t in $$(storm list | sed -n '/^-----/,$$ {s: \+.*$$::; p}' | tail -n+2); do touch $(status_location)/$$t.alive; done

.PHONY: deploy-all kill-all alive-list
.PHONY: $(deploy_targets) $(kill_targets)
