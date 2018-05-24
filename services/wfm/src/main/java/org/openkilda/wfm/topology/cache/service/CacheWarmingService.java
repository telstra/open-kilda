package org.openkilda.wfm.topology.cache.service;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.flow.FlowCreateRequest;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.info.event.PortChangeType;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.ImmutablePair;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.pce.cache.FlowCache;
import org.openkilda.pce.cache.NetworkCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class CacheWarmingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheWarmingService.class);
    private final List<MutablePair<Flow, CachedFlowState>> predefinedFlows = new ArrayList<>();

    private NetworkCache networkCache;

    public CacheWarmingService(NetworkCache networkCache) {
        this.networkCache = networkCache;
    }

    public List<PortInfoData> getPortsForDiscovering(String switchId) {
        List<IslInfoData> links = getActiveLinks(switchId);
        return links.stream()
                .map(IslInfoData::getPath)
                .flatMap(List::stream)
                .map(isl -> new PortInfoData(isl.getSwitchId(), isl.getPortNo(), PortChangeType.CACHED))
                .collect(Collectors.toList());
    }

    /**
     * Get links with active switches.
     * @param switchId current switch id
     * @return links witch active switches
     */
    private List<IslInfoData> getActiveLinks(String switchId) {
        return networkCache.getIslsBySwitch(switchId).stream()
                .filter(isl -> isLinkAvailable(isl.getPath(), switchId))
                .collect(Collectors.toList());
    }

    /**
     * Check whether all switches of link are active.
     * @param nodes nodes of link
     * @param currentSwitchId id of current switch
     * @return result of checking
     */
    private boolean isLinkAvailable(List<PathNode> nodes, String currentSwitchId) {
        return nodes.stream()
                .allMatch(node -> networkCache.getSwitch(node.getSwitchId()).getState().isActive() ||
                StringUtils.equals(currentSwitchId, node.getSwitchId()));
    }

    public List<CommandData> getFlowCommands(String switchId) {
        List<CommandData> result = new ArrayList<>();
        predefinedFlows.stream()
                .filter(flow -> switchId.equals(flow.getLeft().getSourceSwitch()))
                .forEach(pair -> {
                    CommandData command = getFlowCommandIfNeeded(pair.getLeft());
                    if (Objects.nonNull(command)) {
                        result.add(command);
                    }
                });

        return result;
    }

    private CommandData getFlowCommandIfNeeded(Flow flow) {
        CommandData request = null;

        if (flow.getState() == FlowState.CACHED && isFlowSwitchesUp(flow)) {
            request = new FlowCreateRequest(flow);
            LOGGER.info("Created flow create request for flowId {}", flow.getFlowId());
            predefinedFlows.stream()
                    .filter(item -> item.getLeft().equals(flow))
                    .findFirst()
                    .ifPresent(pair -> pair.setRight(CachedFlowState.CREATED));
        }

        return request;
    }

    private boolean isFlowSwitchesUp(Flow flow) {
        return Objects.nonNull(flow) && flow.getFlowPath().getPath()
                .stream()
                .allMatch(node ->
                        networkCache.getSwitch(node.getSwitchId()).getState().isActive());
    }

    /**
     * Put flows to local storage with cached state.
     * @param flows that will be added
     */
    public void addPredefinedFlow(ImmutablePair<Flow, Flow> flows) {
        if (Objects.nonNull(flows.getLeft())) {
            predefinedFlows.add(new MutablePair<>(flows.getLeft(), CachedFlowState.CACHED));
        }
        if (Objects.nonNull(flows.getRight())) {
            predefinedFlows.add(new MutablePair<>(flows.getRight(), CachedFlowState.CACHED));
        }
    }

    private enum CachedFlowState {
        CACHED, CREATED
    }
}
