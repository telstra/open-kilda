/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.wfm.topology.cache.service;

import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.flow.FlowCreateRequest;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.info.event.PortChangeType;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.model.FlowDto;
import org.openkilda.messaging.model.FlowPairDto;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.cache.NetworkCache;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CacheWarmingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheWarmingService.class);
    private final List<MutablePair<FlowDto, CachedFlowState>> predefinedFlows = new ArrayList<>();

    private NetworkCache networkCache;

    public CacheWarmingService(NetworkCache networkCache) {
        this.networkCache = networkCache;
    }

    /**
     * Return list of ports for discovering.
     *
     * @param switchId switch id.
     * @return list of ports for discovering.
     */
    public List<PortInfoData> getPortsForDiscovering(SwitchId switchId) {
        List<IslInfoData> links = getActiveLinks(switchId);
        //TODO(siakovenko): to be removed along with CacheTopology refactoring.
        return links.stream()
                .flatMap(link -> Stream.<PathNode>builder().add(link.getSource()).add(link.getDestination()).build())
                .map(isl -> new PortInfoData(isl.getSwitchId(), isl.getPortNo(), PortChangeType.CACHED))
                .collect(Collectors.toList());
    }

    /**
     * Get links with active switches.
     * @param switchId current switch id
     * @return links witch active switches
     */
    private List<IslInfoData> getActiveLinks(SwitchId switchId) {
        return networkCache.getIslsBySwitch(switchId).stream()
                .filter(isl -> isLinkAvailable(isl.getSource(), isl.getDestination(), switchId))
                .collect(Collectors.toList());
    }

    /**
     * Check whether all switches of link are active.
     * @param source link source
     * @param destination link destination
     * @param currentSwitchId id of current switch
     * @return result of checking
     */
    private boolean isLinkAvailable(PathNode source, PathNode destination, SwitchId currentSwitchId) {
        //TODO(siakovenko): to be removed along with CacheTopology refactoring.
        return Stream.<PathNode>builder().add(source).add(destination).build()
                .allMatch(node -> networkCache.getSwitch(node.getSwitchId()).getState().isActive()
                        || StringUtils.equals(currentSwitchId.toString(), node.getSwitchId().toString()));
    }

    /**
     * Return list of commands in flow.
     *
     * @param switchId switch id.
     * @return list of commands in flow.
     */
    public List<CommandData> getFlowCommands(SwitchId switchId) {
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

    private CommandData getFlowCommandIfNeeded(FlowDto flow) {
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

    private boolean isFlowSwitchesUp(FlowDto flow) {
        return Objects.nonNull(flow) && flow.getFlowPath().getPath()
                .stream()
                .allMatch(node ->
                        networkCache.getSwitch(node.getSwitchId()).getState().isActive());
    }

    /**
     * Put flows to local storage with cached state.
     * @param flows that will be added
     */
    public void addPredefinedFlow(FlowPairDto<FlowDto, FlowDto> flows) {
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
