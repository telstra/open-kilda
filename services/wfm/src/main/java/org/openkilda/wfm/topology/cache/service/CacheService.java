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

import org.openkilda.messaging.command.switches.SwitchDeleteRequest;
import org.openkilda.messaging.ctrl.state.NetworkDump;
import org.openkilda.messaging.error.CacheException;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.NetworkTopologyChange;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.Sender;
import org.openkilda.wfm.share.cache.NetworkCache;
import org.openkilda.wfm.share.mappers.IslMapper;
import org.openkilda.wfm.share.mappers.SwitchMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.stream.Collectors;

public class CacheService {

    private static final Logger logger = LoggerFactory.getLogger(CacheService.class);

    /**
     * Network cache.
     */
    private NetworkCache networkCache;

    private FlowRepository flowRepository;

    public CacheService(NetworkCache networkCache, RepositoryFactory repositoryFactory) {
        this.networkCache = networkCache;

        flowRepository = repositoryFactory.createFlowRepository();

        logger.info("Request initial network state");

        initNetwork(repositoryFactory.createSwitchRepository(), repositoryFactory.createIslRepository());
    }

    private void initNetwork(SwitchRepository switchRepository, IslRepository islRepository) {
        logger.info("Network Cache: Initializing");
        Set<SwitchInfoData> switches = switchRepository.findAll().stream()
                .map(SwitchMapper.INSTANCE::map)
                .collect(Collectors.toSet());
        Set<IslInfoData> links = islRepository.findAll().stream()
                .map(IslMapper.INSTANCE::map)
                .collect(Collectors.toSet());

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
     * @param sw            the switch info data
     * @param sender        the sender
     * @param correlationId the correlation id
     */
    public void handleSwitchEvent(SwitchInfoData sw, ISender sender, String correlationId) {
        logger.debug("State update switch {} message {}", sw.getSwitchId(), sw.getState());

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
     * @param isl           the ISL info data
     * @param sender        the sender
     * @param correlationId the correlation id
     */
    public void handleIslEvent(IslInfoData isl, ISender sender, String correlationId) {
        logger.debug("State update isl {} message cached {}", isl.getId(), isl.getState());

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

                PathNode node = isl.getSource();
                if (node.getSeqId() != 0) {
                    throw new IllegalArgumentException("The first node in path must have seqId = 0.");
                }

                Iterable<String> affectedFlows =
                        flowRepository.findActiveFlowIdsWithPortInPath(node.getSwitchId(), node.getPortNo());

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
     * @param port          the port info data
     * @param sender        the sender
     * @param correlationId the correlation id
     */
    public void handlePortEvent(PortInfoData port, ISender sender, String correlationId) {
        logger.debug("State update port {}_{} message cached {}",
                port.getSwitchId(), port.getPortNo(), port.getState());

        switch (port.getState()) {
            case DOWN:
            case DELETE:
                Iterable<String> affectedFlows =
                        flowRepository.findActiveFlowIdsWithPortInPath(port.getSwitchId(), port.getPortNo());
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
     * @param sender         the sender
     * @param correlationId  the correlation id
     */
    public void handleNetworkTopologyChange(NetworkTopologyChange topologyChange,
                                            ISender sender,
                                            String correlationId) {
        Iterable<String> affectedFlows;

        switch (topologyChange.getType()) {
            case ENDPOINT_DROP:
                // TODO(surabujin): need implementation
                return;

            case ENDPOINT_ADD:
                affectedFlows = flowRepository.findDownFlowIds();
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

    private void emitRerouteCommands(ISender sender, Iterable<String> flows,
                                     String initialCorrelationId, String reason) {
        for (String flowId : flows) {
            String correlationId = format("%s-%s", initialCorrelationId, flowId);

            sender.sendCommandToWfmReroute(flowId, correlationId);

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
     * Deletes a switch from a cache.
     *
     * @param request       the instance of {@link SwitchDeleteRequest}.
     * @param sender        the instance of {@link Sender}.
     * @param correlationId the log correlation id.
     */
    public void deleteSwitch(SwitchDeleteRequest request, Sender sender, String correlationId) {
        logger.debug("Switch {} is being deleted, correlationId: {}", request.getSwitchId(), correlationId);
        networkCache.deleteSwitch(request.getSwitchId());
        logger.debug("Switch {} has been deleted, correlationId: {}", request.getSwitchId(), correlationId);
    }
}
