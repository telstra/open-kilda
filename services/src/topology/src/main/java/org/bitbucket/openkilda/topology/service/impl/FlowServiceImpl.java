/* Copyright 2017 Telstra Open Source
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

package org.bitbucket.openkilda.topology.service.impl;

import static java.util.stream.Collectors.toMap;

import org.bitbucket.openkilda.messaging.Destination;
import org.bitbucket.openkilda.messaging.command.CommandMessage;
import org.bitbucket.openkilda.messaging.error.ErrorType;
import org.bitbucket.openkilda.messaging.error.MessageException;
import org.bitbucket.openkilda.messaging.info.InfoMessage;
import org.bitbucket.openkilda.messaging.info.flow.FlowPathResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowsResponse;
import org.bitbucket.openkilda.messaging.payload.ResourcePool;
import org.bitbucket.openkilda.messaging.payload.flow.FlowEndpointPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowPathPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowsPayload;
import org.bitbucket.openkilda.topology.domain.Flow;
import org.bitbucket.openkilda.topology.domain.Isl;
import org.bitbucket.openkilda.topology.domain.Switch;
import org.bitbucket.openkilda.topology.domain.repository.FlowRepository;
import org.bitbucket.openkilda.topology.domain.repository.IslRepository;
import org.bitbucket.openkilda.topology.domain.repository.SwitchRepository;
import org.bitbucket.openkilda.topology.service.FlowService;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Manages operations on flows.
 */
@Service
@Transactional
public class FlowServiceImpl implements FlowService {
    /**
     * Direct flow cookie mask.
     */
    public static final long DIRECT_FLOW_COOKIE = 0x4000000000000000L;
    /**
     * Reverse flow cookie mask.
     */
    public static final long REVERSE_FLOW_COOKIE = 0x2000000000000000L;
    /**
     * Cookie value mask.
     */
    private static final long COOKIE_MASK = 0x00000000FFFFFFFFL;
    /**
     * Minimum vlan id value.
     */
    private static final int MIN_COOKIE_VALUE = 1;

    /**
     * Maximum vlan id value.
     */
    private static final int MAX_COOKIE_VALUE = 0x7FFFFFFE;

    /**
     * Minimum vlan id value.
     */
    private static final int MIN_VLAN_ID = 2;

    /**
     * Maximum vlan id value.
     */
    private static final int MAX_VLAN_ID = 4094;

    /**
     * The logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(FlowServiceImpl.class);

    /**
     * Transit vlan id resource allocator.
     */
    private static final ResourcePool transitVlanIdPool = new ResourcePool(MIN_VLAN_ID, MAX_VLAN_ID);

    /**
     * Cookie resource allocator.
     */
    private static final ResourcePool cookiePool = new ResourcePool(MIN_COOKIE_VALUE, MAX_COOKIE_VALUE);

    /**
     * Inventory repository.
     */
    @Autowired
    private SwitchRepository switchRepository;

    /**
     * Isl repository.
     */
    @Autowired
    private IslRepository islRepository;

    /**
     * Inventory repository.
     */
    @Autowired
    private FlowRepository flowRepository;

    /**
     * Converts {@link Flow} instance to {@link FlowPayload} instance.
     *
     * @param flow {@link Flow} instance
     * @return {@link FlowPayload} instance
     */
    private static FlowPayload getFlowPayloadByFlow(final Flow flow) {
        return new FlowPayload(flow.getFlowId(), flow.getCookie() & COOKIE_MASK, new FlowEndpointPayload(
                flow.getSourceSwitch(), flow.getSourcePort(), flow.getSourceVlan(), null), new FlowEndpointPayload(
                flow.getDestinationSwitch(), flow.getDestinationPort(), flow.getDestinationVlan(), null),
                flow.getBandwidth(), flow.getDescription(), flow.getLastUpdated(), null);
    }

    /**
     * Sorts path between two switches.
     *
     * @param source source switch datapath id
     * @param links  list of {@link Isl} instances
     * @return sorted path of {@link Isl} instances
     */
    private static List<Isl> sortPath(String source, List<Isl> links) {
        Map<String, Isl> map = links.stream().collect(toMap(Isl::getSourceSwitch, Function.identity()));
        List<Isl> path = new ArrayList<>(links.size());
        Isl first = map.get(source);
        path.add(first);
        Isl next = map.get(first.getDestinationSwitch());
        while (next != null) {
            path.add(next);
            next = map.get(next.getDestinationSwitch());
        }
        return path;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<CommandMessage> createFlow(final FlowPayload payload, final String correlationId) {
        Switch source = switchRepository.findByName(payload.getSource().getSwitchId());
        Switch destination = switchRepository.findByName(payload.getDestination().getSwitchId());

        if (source == null || destination == null) {
            logger.error("Switches not found: source={}, destination={}",
                    payload.getSource().getSwitchId(), payload.getDestination().getSwitchId());
            throw new MessageException(ErrorType.NOT_FOUND, System.currentTimeMillis());
        }

        List<Isl> path = islRepository.getPath(source.getName(), destination.getName());

        if (path == null || path.isEmpty()) {
            logger.error("Path not found: source={}, destination={}",
                    payload.getSource().getSwitchId(), payload.getDestination().getSwitchId());
            throw new MessageException(ErrorType.NOT_FOUND, System.currentTimeMillis());
        }

        List<Isl> sortedPath = sortPath(source.getName(), path);
        logger.debug("Path found: {}", sortedPath);

        int directVlanId = transitVlanIdPool.allocate();
        int reverseVlanId = transitVlanIdPool.allocate();
        long cookie = getCookie();

        Flow direct = buildFlow(path, source, destination, payload, directVlanId, cookie | DIRECT_FLOW_COOKIE);
        Flow reverse = buildFlow(path, destination, source, payload, reverseVlanId, cookie | REVERSE_FLOW_COOKIE);

        flowRepository.save(direct);
        logger.debug("Flow stored: flow={}", direct);
        flowRepository.save(reverse);
        logger.debug("Flow stored: flow={}", reverse);

        Set<CommandMessage> response = new HashSet<>();
        response.addAll(direct.getInstallationCommands(sortedPath, correlationId));
        response.addAll(reverse.getInstallationCommands(sortedPath, correlationId));

        logger.debug("Flows create command message list: {}", response);

        return response;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<CommandMessage> updateFlow(final FlowPayload payload, final String correlationId) {
        Set<Flow> oldFlows = findFlow(payload.getId());
        Set<CommandMessage> commands = new HashSet<>();
        commands.addAll(createFlow(payload, correlationId));
        commands.addAll(deleteFlow(oldFlows, correlationId));
        return commands;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InfoMessage getFlow(final FlowIdStatusPayload payload, final String correlationId) {
        Set<Flow> flows = flowRepository.findByFlowId(payload.getId());
        if (flows == null || flows.isEmpty()) {
            logger.error("Flow with id={} not found", payload.getId());
            throw new MessageException(ErrorType.NOT_FOUND, System.currentTimeMillis());
        }

        FlowResponse response = null;

        for (Flow flow : flows) {
            if ((flow.getCookie() & DIRECT_FLOW_COOKIE) == DIRECT_FLOW_COOKIE) {
                response = new FlowResponse(getFlowPayloadByFlow(flow));
            }
        }

        logger.debug("Flow with id={} get: {}", payload.getId(), response);

        return new InfoMessage(response, System.currentTimeMillis(), correlationId, Destination.WFM);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InfoMessage getFlows(final FlowIdStatusPayload payload, final String correlationId) {
        Set<Flow> flows = flowRepository.findAll();

        List<FlowPayload> flowsPayload = new ArrayList<>(flows.size() / 2);

        for (Flow flow : flows) {
            if ((flow.getCookie() & DIRECT_FLOW_COOKIE) == DIRECT_FLOW_COOKIE) {
                flowsPayload.add(getFlowPayloadByFlow(flow));
            }
        }

        logger.debug("Flows get: {}", flowsPayload);

        return new InfoMessage(new FlowsResponse(new FlowsPayload(flowsPayload)),
                System.currentTimeMillis(), correlationId, Destination.WFM);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InfoMessage pathFlow(final FlowIdStatusPayload payload, final String correlationId) {
        Set<Flow> flows = flowRepository.findByFlowId(payload.getId());

        FlowPathPayload flowPathPayload = null;
        for (Flow flow : flows) {
            if ((flow.getCookie() & DIRECT_FLOW_COOKIE) == DIRECT_FLOW_COOKIE) {
                flowPathPayload = new FlowPathPayload(flow.getFlowId(), flow.getFlowPath());
            }
        }

        logger.debug("Flow with id={} path: {}", payload.getId(), flowPathPayload);

        return new InfoMessage(new FlowPathResponse(flowPathPayload),
                System.currentTimeMillis(), correlationId, Destination.WFM);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<CommandMessage> deleteFlow(final FlowIdStatusPayload payload, final String correlationId) {
        Set<Flow> flows = findFlow(payload.getId());
        return deleteFlow(flows, correlationId);
    }

    /**
     * Removes given flows from database and forms removal commands.
     *
     * @param flows         collection of {@link Flow} instances
     * @param correlationId request correlation id
     * @return set of removal commands
     */
    private Set<CommandMessage> deleteFlow(final Set<Flow> flows, final String correlationId) {
        Set<CommandMessage> response = new HashSet<>();
        for (Flow flow : flows) {
            response.addAll(flow.getDeletionCommands(correlationId));
            flowRepository.delete(flow);
            cookiePool.deallocate((int) (flow.getCookie()));
            transitVlanIdPool.deallocate(flow.getTransitVlan());
            logger.debug("Flow deleted: {}", flows);
        }
        logger.debug("Flows delete command message list: {}", response);
        return response;
    }

    /**
     * Finds flow by flow id.
     *
     * @param flowId flow id
     * @return direct and reverse {@link Flow} instances as set
     */
    private Set<Flow> findFlow(final String flowId) {
        Set<Flow> flows = flowRepository.findByFlowId(flowId);

        if (flows == null || flows.isEmpty()) {
            logger.error("Could not find flows: id={}", flowId);
            throw new MessageException(ErrorType.NOT_FOUND, System.currentTimeMillis());
        }

        logger.debug("Flows with id={} found: flows={}", flowId, flows);

        return flows;
    }

    /**
     * Creates flow for further saving in database.
     *
     * @return {@link ImmutablePair} of direct and reverse {@link Flow} instances
     */
    private Flow buildFlow(final List<Isl> path, final Switch source, final Switch destination,
                           final FlowPayload payload, final int transitVlanId, final long cookie) {
        List<String> pathToStore = new ArrayList<>(path.size() + 1);
        pathToStore.add(payload.getSource().getSwitchId());
        for (Isl isl : path) {
            pathToStore.add(isl.getDestinationSwitch());
        }

        Flow flow = new Flow(source, destination, payload.getId(), cookie, pathToStore, payload.getMaximumBandwidth(),
                payload.getSource().getSwitchId(), payload.getSource().getPortId(), payload.getSource().getVlanId(),
                payload.getDestination().getSwitchId(), payload.getDestination().getPortId(),
                payload.getDestination().getVlanId(), payload.getDescription(), getTime(), transitVlanId);

        logger.debug("Flow prepared: flow={}", flow);

        return flow;
    }

    /**
     * Generates new cookie.
     *
     * @return new allocated cookie
     */
    private long getCookie() {
        //return UUID.randomUUID().getLeastSignificantBits() & COOKIE_MASK;
        return cookiePool.allocate();
    }

    /**
     * Generates last updated field value in ISO format.
     *
     * @return last updated string field value
     */
    private String getTime() {
        return ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT);
    }
}
