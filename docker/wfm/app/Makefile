# WFM topology management

TOPOLOGY := network flowhs stats isllatency opentsdb portstate nbworker ping reroute swmanager floodlightrouter statsrouter connecteddevices server42-control

ENTRY_network := org.openkilda.wfm.topology.network.NetworkTopology
ENTRY_flowhs := org.openkilda.wfm.topology.flowhs.FlowHsTopology
ENTRY_stats := org.openkilda.wfm.topology.stats.StatsTopology
ENTRY_isllatency := org.openkilda.wfm.topology.isllatency.IslLatencyTopology
ENTRY_connecteddevices := org.openkilda.wfm.topology.connecteddevices.ConnectedDevicesTopology
ENTRY_opentsdb := org.openkilda.wfm.topology.opentsdb.OpenTsdbTopology
ENTRY_portstate := org.openkilda.wfm.topology.portstate.PortStateTopology
ENTRY_nbworker := org.openkilda.wfm.topology.nbworker.NbWorkerTopology
ENTRY_ping := org.openkilda.wfm.topology.ping.PingTopology
ENTRY_reroute := org.openkilda.wfm.topology.reroute.RerouteTopology
ENTRY_swmanager := org.openkilda.wfm.topology.switchmanager.SwitchManagerTopology
ENTRY_floodlightrouter := org.openkilda.wfm.topology.floodlightrouter.FloodlightRouterTopology
ENTRY_statsrouter := org.openkilda.wfm.topology.statsrouter.StatsRouterTopology

TOPOLOGY_JAR := target/WorkflowManager-1.0-SNAPSHOT-jar-with-dependencies.jar
TOPOLOGY_CONFIG := topology.properties

STORM := /opt/storm/bin/storm

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
