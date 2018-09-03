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

import static java.lang.String.format;

import org.openkilda.messaging.ctrl.state.FlowDump;
import org.openkilda.messaging.ctrl.state.NetworkDump;
import org.openkilda.messaging.error.CacheException;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.NetworkTopologyChange;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.flow.FlowInfoData;
import org.openkilda.messaging.model.BidirectionalFlow;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.FlowPair;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.pce.cache.FlowCache;
import org.openkilda.pce.cache.NetworkCache;
import org.openkilda.pce.provider.PathComputer;
import org.openkilda.wfm.share.utils.PathComputerFlowFetcher;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class CacheService {

    private static final Logger logger = LoggerFactory.getLogger(CacheService.class);

    /**
     * Network cache.
     */
    private NetworkCache networkCache;

    /**
     * Flow cache.
     */
    private FlowCache flowCache;

    public CacheService(NetworkCache networkCache, FlowCache flowCache, PathComputer pathComputer) {
        this.networkCache = networkCache;
        this.flowCache = flowCache;

        logger.info("Request initial network state");

        initFlowCache(pathComputer);
        initNetwork(pathComputer);
    }

    private void initFlowCache(PathComputer pathComputer) {
        logger.info("Flow Cache: Initializing");
        PathComputerFlowFetcher flowFetcher = new PathComputerFlowFetcher(pathComputer);

        for (BidirectionalFlow bidirectionalFlow : flowFetcher.getFlows()) {
            FlowPair<Flow, Flow> flowPair = new FlowPair<>(
                    bidirectionalFlow.getForward(), bidirectionalFlow.getReverse());
            flowCache.pushFlow(flowPair);
        }
        logger.info("Flow Cache: Initialized");
    }

    private void initNetwork(PathComputer pathComputer) {
        logger.info("Network Cache: Initializing");
        Set<SwitchInfoData> switches = new HashSet<>(pathComputer.getSwitches());
        Set<IslInfoData> links = new HashSet<>(pathComputer.getIsls());

        logger.info("Network Cache: Initializing - {} Switches (size)", switches.size());
        logger.info("Network Cache: Initializing - {} ISLs (size)", links.size());

        //
        // We will call createOrUpdate to ensure we can ignore duplicates.
        //
        // The alternative is to call networkCache::createOrUpdateSwitch / networkCache::createOrUpdateIsl
        // networkCache.load(switches, links);

        switches.forEach(networkCache::createOrUpdateSwitch);

        for (IslInfoData isl : links) {
            try {
                if (isl.isSelfLooped()) {
                    logger.warn("Skipped self-looped ISL: {}", isl);
                } else {
                    networkCache.createOrUpdateIsl(isl);
                }
            } catch (Exception e) {
                logger.error("CacheBolt :: initNetwork :: add ISL caused error --> isl = {} ; Exception = {}", isl, e);
            }
        }

        logger.info("Network Cache: Initialized");
    }

    /**
     * Handle switch info event and send according messages to topology if needed.
     *
     * @param sw the switch info data
     * @param sender the sender
     * @param correlationId the correlation id
     */
    public void handleSwitchEvent(SwitchInfoData sw, ISender sender, String correlationId) {
        logger.debug("State update switch {} message {}", sw.getSwitchId(), sw.getState());
        Set<FlowPair<Flow, Flow>> affectedFlows;

        switch (sw.getState()) {

            case ADDED:
            case ACTIVATED:
                onSwitchUp(sw);
                break;

            case REMOVED:
            case DEACTIVATED:
                if (networkCache.cacheContainsSwitch(sw.getSwitchId())) {
                    networkCache.updateSwitch(sw);
                }

                // (crimi - 2018.04.17) - eliminating taking action on Switch down events ..
                // primarily because floodlight can regularly drop a connection to the switch (or
                // vice versa) and a new connection is made almost immediately. Essentially, a flap.
                // Rather than reroute here .. what to see if an ISL goes down.  This introduces a
                // longer delay .. but a necessary dampening affect.  The better solution
                // is to kick of an immediate probe if we get such an event .. and the probe
                // should confirm what is really happening.
                //affectedFlows = flowCache.getActiveFlowsWithAffectedPath(sw.getSwitchId());
                //String reason = String.format("switch %s is %s", sw.getSwitchId(), sw.getState());
                //emitRerouteCommands(affectedFlows, tuple, correlationId, FlowOperation.UPDATE, reason);
                break;

            case CACHED:
                break;
            case CHANGED:
                break;

            default:
                logger.warn("Unknown state update switch info message");
                break;
        }
    }

    /**
     * Handle ISL info event and send according messages to topology if needed.
     *
     * @param isl the ISL info data
     * @param sender the sender
     * @param correlationId the correlation id
     */
    public void handleIslEvent(IslInfoData isl, ISender sender, String correlationId) {
        logger.debug("State update isl {} message cached {}", isl.getId(), isl.getState());
        Set<FlowPair<Flow, Flow>> affectedFlows;

        switch (isl.getState()) {
            case DISCOVERED:
                if (networkCache.cacheContainsIsl(isl.getId())) {
                    networkCache.updateIsl(isl);
                } else {
                    if (isl.isSelfLooped()) {
                        logger.warn("Skipped self-looped ISL: {}", isl);
                    } else {
                        networkCache.createIsl(isl);
                    }
                }
                break;

            case FAILED:
            case MOVED:
                try {
                    networkCache.deleteIsl(isl.getId());
                } catch (CacheException exception) {
                    logger.warn("{}:{}", exception.getErrorMessage(), exception.getErrorDescription());
                }

                affectedFlows = flowCache.getActiveFlowsWithAffectedPath(isl);
                String reason = String.format("isl %s FAILED", isl.getId());
                emitRerouteCommands(sender, affectedFlows, correlationId, reason);
                break;

            case OTHER_UPDATE:
                break;

            case CACHED:
                break;

            default:
                logger.warn("Unknown state update isl info message");
                break;
        }
    }

    /**
     * Handle port info event and send according messages to topology if needed.
     *
     * @param port the port info data
     * @param sender the sender
     * @param correlationId the correlation id
     */
    public void handlePortEvent(PortInfoData port, ISender sender, String correlationId) {
        logger.debug("State update port {}_{} message cached {}",
                port.getSwitchId(), port.getPortNo(), port.getState());

        switch (port.getState()) {
            case DOWN:
            case DELETE:
                Set<FlowPair<Flow, Flow>> affectedFlows = flowCache.getActiveFlowsWithAffectedPath(port);
                String reason = String.format("port %s_%s is %s",
                        port.getSwitchId(), port.getPortNo(), port.getState());
                emitRerouteCommands(sender, affectedFlows, correlationId, reason);
                break;

            case UP:
            case ADD:
                break;

            case OTHER_UPDATE:
            case CACHED:
                break;

            default:
                logger.warn("Unknown state update isl info message");
                break;
        }
    }

    /**
     * Handle network topology change event and send according messages to topology if needed.
     *
     * @param topologyChange the network topology change event
     * @param sender the sender
     * @param correlationId the correlation id
     */
    public void handleNetworkTopologyChange(NetworkTopologyChange topologyChange,
                                            ISender sender,
                                            String correlationId) {
        Set<FlowPair<Flow, Flow>> affectedFlows;

        switch (topologyChange.getType()) {
            case ENDPOINT_DROP:
                // TODO(surabujin): need implementation
                return;

            case ENDPOINT_ADD:
                affectedFlows = getFlowsForRerouting();
                break;

            default:
                logger.error("Unhandled reroute type: {}", topologyChange.getType());
                return;
        }
        String reason = String.format("network topology change  %s_%s is %s",
                topologyChange.getSwitchId(), topologyChange.getPortNumber(),
                topologyChange.getType());
        emitRerouteCommands(sender, affectedFlows, correlationId, reason);
    }

    public NetworkDump getNetworkDump() {
        return new NetworkDump(networkCache.dumpSwitches(), networkCache.dumpIsls());
    }

    public FlowDump getFlowDump() {
        return new FlowDump(flowCache.dumpFlows());
    }

    public Set<Integer> getAllocatedVlans() {
        return flowCache.getAllocatedVlans();
    }

    public Set<Integer> getAllocatedCookies() {
        return flowCache.getAllocatedCookies();
    }

    public Map<SwitchId, Set<Integer>> getAllocatedMeters() {
        return flowCache.getAllocatedMeters();
    }

    private Set<FlowPair<Flow, Flow>> getFlowsForRerouting() {
        Set<FlowPair<Flow, Flow>> inactiveFlows = flowCache.dumpFlows().stream()
                .filter(flow -> FlowState.DOWN.equals(flow.getLeft().getState()))
                .collect(Collectors.toSet());

        return inactiveFlows;
    }

    private void emitRerouteCommands(ISender sender, Set<FlowPair<Flow, Flow>> flows,
                                     String initialCorrelationId, String reason) {
        for (FlowPair<Flow, Flow> flow : flows) {
            final String flowId = flow.getLeft().getFlowId();

            String correlationId = format("%s-%s", initialCorrelationId, flowId);

            sender.sendCommandToWfmReroute(flowId, correlationId);

            flow.getLeft().setState(FlowState.DOWN);
            flow.getRight().setState(FlowState.DOWN);

            logger.warn("Flow {} reroute command message sent with correlationId {}, reason {}",
                    flowId, correlationId, reason);
        }
    }

    private void onSwitchUp(SwitchInfoData sw) {
        logger.info("Switch {} is {}", sw.getSwitchId(), sw.getState().getType());
        if (networkCache.cacheContainsSwitch(sw.getSwitchId())) {
            networkCache.updateSwitch(sw);
        } else {
            networkCache.createSwitch(sw);
        }
    }

    /**
     * Handle flow info event and send according messages to topology if needed.
     *
     * @param flowData the flow info data
     * @param sender the sender
     * @param correlationId the correlation id
     * @throws JsonProcessingException if sending message can't be serialized as json
     */
    public void handleFlowEvent(FlowInfoData flowData, ISender sender, String correlationId)
            throws JsonProcessingException {
        switch (flowData.getOperation()) {
            case PUSH:
            case PUSH_PROPAGATE:
                logger.debug("Flow {} message received: {}, correlationId: {}", flowData.getOperation(), flowData,
                        correlationId);
                flowCache.putFlow(flowData.getPayload());
                // do not emit to TPE .. NB will send directly
                break;

            case UNPUSH:
            case UNPUSH_PROPAGATE:
                logger.trace("Flow {} message received: {}, correlationId: {}", flowData.getOperation(), flowData,
                        correlationId);
                String flowsId2 = flowData.getPayload().getLeft().getFlowId();
                flowCache.removeFlow(flowsId2);
                logger.debug("Flow {} message processed: {}, correlationId: {}", flowData.getOperation(), flowData,
                        correlationId);
                break;


            case CREATE:
                // TODO: This should be more lenient .. in case of retries
                logger.trace("Flow create message received: {}, correlationId: {}", flowData, correlationId);
                flowCache.putFlow(flowData.getPayload());
                sender.sendInfoToTopologyEngine(flowData, flowData.getCorrelationId());
                logger.debug("Flow create message sent: {}, correlationId: {}", flowData, correlationId);
                break;

            case DELETE:
                // TODO: This should be more lenient .. in case of retries
                logger.trace("Flow remove message received: {}, correlationId: {}", flowData, correlationId);
                String flowsId = flowData.getPayload().getLeft().getFlowId();
                flowCache.removeFlow(flowsId);
                sender.sendInfoToTopologyEngine(flowData, flowData.getCorrelationId());
                logger.debug("Flow remove message sent: {}, correlationId: {} ", flowData, correlationId);
                break;

            case UPDATE:
                logger.trace("Flow update message received: {}, correlationId: {}", flowData, correlationId);
                // TODO: This should be more lenient .. in case of retries
                flowCache.putFlow(flowData.getPayload());
                sender.sendInfoToTopologyEngine(flowData, flowData.getCorrelationId());
                logger.debug("Flow update message sent: {}, correlationId: {}", flowData, correlationId);
                break;

            case STATE:
                flowCache.putFlow(flowData.getPayload());
                logger.debug("Flow state changed: {}, correlationId: {}", flowData, correlationId);
                break;

            case CACHE:
                logger.debug("Sync flow cache message received: {}, correlationId: {}", flowData, correlationId);
                if (flowData.getPayload() != null) {
                    flowCache.putFlow(flowData.getPayload());
                } else {
                    flowCache.removeFlow(flowData.getFlowId());
                }
                break;

            default:
                logger.warn("Skip undefined flow operation {}", flowData);
                break;
        }
    }
}
