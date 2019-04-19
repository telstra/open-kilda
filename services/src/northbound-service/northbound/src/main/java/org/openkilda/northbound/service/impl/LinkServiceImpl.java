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

package org.openkilda.northbound.service.impl;

import static org.openkilda.northbound.utils.async.AsyncUtils.collectChunkedResponses;
import static org.openkilda.northbound.utils.async.AsyncUtils.collectResponses;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.MessageException;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.flow.FlowResponse;
import org.openkilda.messaging.info.flow.FlowsResponse;
import org.openkilda.messaging.model.NetworkEndpoint;
import org.openkilda.messaging.model.NetworkEndpointMask;
import org.openkilda.messaging.nbtopology.request.DeleteLinkRequest;
import org.openkilda.messaging.nbtopology.request.GetFlowsForIslRequest;
import org.openkilda.messaging.nbtopology.request.GetLinksRequest;
import org.openkilda.messaging.nbtopology.request.LinkPropsDrop;
import org.openkilda.messaging.nbtopology.request.LinkPropsGet;
import org.openkilda.messaging.nbtopology.request.LinkPropsPut;
import org.openkilda.messaging.nbtopology.request.RerouteFlowsForIslRequest;
import org.openkilda.messaging.nbtopology.request.UpdateLinkEnableBfdRequest;
import org.openkilda.messaging.nbtopology.request.UpdateLinkUnderMaintenanceRequest;
import org.openkilda.messaging.nbtopology.response.LinkPropsData;
import org.openkilda.messaging.nbtopology.response.LinkPropsResponse;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.model.LinkProps;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.converter.FlowMapper;
import org.openkilda.northbound.converter.LinkMapper;
import org.openkilda.northbound.converter.LinkPropsMapper;
import org.openkilda.northbound.dto.BatchResults;
import org.openkilda.northbound.dto.v1.links.LinkDto;
import org.openkilda.northbound.dto.v1.links.LinkEnableBfdDto;
import org.openkilda.northbound.dto.v1.links.LinkMaxBandwidthDto;
import org.openkilda.northbound.dto.v1.links.LinkMaxBandwidthRequest;
import org.openkilda.northbound.dto.v1.links.LinkParametersDto;
import org.openkilda.northbound.dto.v1.links.LinkPropsDto;
import org.openkilda.northbound.dto.v1.links.LinkUnderMaintenanceDto;
import org.openkilda.northbound.messaging.MessagingChannel;
import org.openkilda.northbound.service.LinkService;
import org.openkilda.northbound.utils.CorrelationIdFactory;
import org.openkilda.northbound.utils.RequestCorrelationId;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class LinkServiceImpl implements LinkService {

    private static final Logger logger = LoggerFactory.getLogger(LinkServiceImpl.class);

    @Autowired
    private CorrelationIdFactory idFactory;

    @Autowired
    private LinkMapper linkMapper;

    @Autowired
    private FlowMapper flowMapper;

    @Autowired
    private LinkPropsMapper linkPropsMapper;

    /**
     * The kafka topic for the nb topology.
     */
    @Value("#{kafkaTopicsConfig.getTopoNbTopic()}")
    private String nbworkerTopic;

    @Autowired
    private MessagingChannel messagingChannel;

    @Override
    public CompletableFuture<List<LinkDto>> getLinks(SwitchId srcSwitch, Integer srcPort,
                                                     SwitchId dstSwitch, Integer dstPort) {
        final String correlationId = RequestCorrelationId.getId();
        logger.debug("Get links request received");
        GetLinksRequest request = null;
        try {
            request = new GetLinksRequest(new NetworkEndpointMask(srcSwitch, srcPort),
                    new NetworkEndpointMask(dstSwitch, dstPort));
        } catch (IllegalArgumentException e) {
            logger.error("Can not parse arguments: {}", e.getMessage());
            throw new MessageException(correlationId, System.currentTimeMillis(), ErrorType.DATA_INVALID,
                    e.getMessage(), "Can not parse arguments when create 'get links' request");
        }
        CommandMessage message = new CommandMessage(request, System.currentTimeMillis(), correlationId);

        return messagingChannel.sendAndGetChunked(nbworkerTopic, message)
                .thenApply(response -> response.stream()
                        .map(IslInfoData.class::cast)
                        .map(linkMapper::toLinkDto)
                        .collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<List<LinkPropsDto>> getLinkProps(SwitchId srcSwitch, Integer srcPort,
                                                              SwitchId dstSwitch, Integer dstPort) {
        final String correlationId = RequestCorrelationId.getId();
        logger.debug("Get link properties request received");
        LinkPropsGet request = new LinkPropsGet(new NetworkEndpointMask(srcSwitch, srcPort),
                new NetworkEndpointMask(dstSwitch, dstPort));
        CommandMessage message = new CommandMessage(request, System.currentTimeMillis(), correlationId);

        return messagingChannel.sendAndGetChunked(nbworkerTopic, message)
                .thenApply(response -> response.stream()
                        .map(LinkPropsData.class::cast)
                        .map(linkPropsMapper::toDto)
                        .collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<BatchResults> setLinkProps(List<LinkPropsDto> linkPropsList) {
        logger.debug("Link props \"SET\" request received (consists of {} records)", linkPropsList.size());

        List<String> errors = new ArrayList<>();
        List<CompletableFuture<?>> pendingRequest = new ArrayList<>(linkPropsList.size());

        for (LinkPropsDto requestItem : linkPropsList) {
            org.openkilda.messaging.model.LinkPropsDto linkProps;
            try {
                linkProps = linkPropsMapper.toLinkProps(requestItem);
            } catch (IllegalArgumentException e) {
                errors.add(e.getMessage());
                continue;
            }
            LinkPropsPut commandRequest = new LinkPropsPut(linkProps);
            String requestId = idFactory.produceChained(RequestCorrelationId.getId());
            CommandMessage message = new CommandMessage(commandRequest, System.currentTimeMillis(), requestId);

            pendingRequest.add(messagingChannel.sendAndGet(nbworkerTopic, message));
        }

        return collectResponses(pendingRequest, LinkPropsResponse.class)
                .thenApply(responses -> getLinkPropsResult(responses, errors));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<LinkMaxBandwidthDto> updateLinkBandwidth(SwitchId srcSwitch, Integer srcPort,
                                                                      SwitchId dstSwitch, Integer dstPort,
                                                                      LinkMaxBandwidthRequest input) {
        if (input.getMaxBandwidth() == null || input.getMaxBandwidth() < 0) {
            throw new MessageException(ErrorType.PARAMETERS_INVALID, "Invalid value of max_bandwidth",
                    "Maximum bandwidth must not be null");
        }
        if (srcPort < 0) {
            throw new MessageException(ErrorType.PARAMETERS_INVALID, "Invalid value of source port",
                    "Port number can't be negative");
        }
        if (dstPort < 0) {
            throw new MessageException(ErrorType.PARAMETERS_INVALID, "Invalid value of destination port",
                    "Port number can't be negative");
        }

        org.openkilda.messaging.model.LinkPropsDto linkProps = org.openkilda.messaging.model.LinkPropsDto.builder()
                .source(new NetworkEndpoint(srcSwitch, srcPort))
                .dest(new NetworkEndpoint(dstSwitch, dstPort))
                .props(ImmutableMap.of(LinkProps.MAX_BANDWIDTH_PROP_NAME,
                        input.getMaxBandwidth().toString()))
                .build();
        LinkPropsPut request = new LinkPropsPut(linkProps);
        String correlationId = RequestCorrelationId.getId();
        CommandMessage message = new CommandMessage(request, System.currentTimeMillis(), correlationId);
        return messagingChannel.sendAndGet(nbworkerTopic, message)
                .thenApply(response -> {
                    if (((LinkPropsResponse) response).getLinkProps() != null) {
                        return linkPropsMapper.toLinkMaxBandwidth(((LinkPropsResponse) response).getLinkProps());
                    } else {
                        throw new MessageException(ErrorType.REQUEST_INVALID,
                                "Requested maximum bandwidth is too small",
                                ((LinkPropsResponse) response).getError());
                    }
                });
    }

    @Override
    public CompletableFuture<BatchResults> delLinkProps(List<LinkPropsDto> linkPropsList) {
        List<CompletableFuture<List<InfoData>>> pendingRequest = new ArrayList<>(linkPropsList.size());

        for (LinkPropsDto requestItem : linkPropsList) {
            LinkPropsDrop request = new LinkPropsDrop(linkPropsMapper.toLinkPropsMask(requestItem));
            String requestId = idFactory.produceChained(RequestCorrelationId.getId());
            CommandMessage message = new CommandMessage(request, System.currentTimeMillis(), requestId);

            pendingRequest.add(messagingChannel.sendAndGetChunked(nbworkerTopic, message));
        }

        return collectChunkedResponses(pendingRequest, LinkPropsResponse.class)
                .thenApply(responses -> getLinkPropsResult(responses, new ArrayList<>()));
    }

    private BatchResults getLinkPropsResult(List<LinkPropsResponse> responses, List<String> errors) {
        int successCount = 0;
        for (LinkPropsResponse linkProps : responses) {
            if (linkProps.isSuccess()) {
                successCount += 1;
            } else {
                errors.add(linkProps.getError());
            }
        }

        return new BatchResults(errors.size(), successCount, errors);
    }

    @Override
    public CompletableFuture<List<FlowPayload>> getFlowsForLink(SwitchId srcSwitch, Integer srcPort,
                                                                SwitchId dstSwitch, Integer dstPort) {
        final String correlationId = RequestCorrelationId.getId();
        logger.debug("Get all flows for a particular link request processing");
        GetFlowsForIslRequest data = null;
        try {
            data = new GetFlowsForIslRequest(new NetworkEndpoint(srcSwitch, srcPort),
                    new NetworkEndpoint(dstSwitch, dstPort), correlationId);
        } catch (IllegalArgumentException e) {
            logger.error("Can not parse arguments: {}", e.getMessage());
            throw new MessageException(correlationId, System.currentTimeMillis(), ErrorType.DATA_INVALID,
                    e.getMessage(), "Can not parse arguments when create \"get flows for link\" request");
        }
        CommandMessage message = new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);

        return messagingChannel.sendAndGetChunked(nbworkerTopic, message)
                .thenApply(response -> response.stream()
                        .map(FlowResponse.class::cast)
                        .map(FlowResponse::getPayload)
                        .map(flowMapper::toFlowOutput)
                        .collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<List<String>> rerouteFlowsForLink(SwitchId srcSwitch, Integer srcPort,
                                                               SwitchId dstSwitch, Integer dstPort) {
        final String correlationId = RequestCorrelationId.getId();
        logger.debug("Reroute all flows for a particular link request processing");
        RerouteFlowsForIslRequest data = null;
        try {
            data = new RerouteFlowsForIslRequest(new NetworkEndpoint(srcSwitch, srcPort),
                    new NetworkEndpoint(dstSwitch, dstPort), correlationId);
        } catch (IllegalArgumentException e) {
            logger.error("Can not parse arguments: {}", e.getMessage());
            throw new MessageException(correlationId, System.currentTimeMillis(), ErrorType.DATA_INVALID,
                    e.getMessage(), "Can not parse arguments when create \"reroute flows for link\" request");
        }
        CommandMessage message = new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);

        return messagingChannel.sendAndGetChunked(nbworkerTopic, message)
                .thenApply(response -> response.stream()
                        .map(FlowsResponse.class::cast)
                        .map(FlowsResponse::getFlowIds)
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<List<LinkDto>> deleteLink(LinkParametersDto linkParameters) {
        final String correlationId = RequestCorrelationId.getId();
        logger.info("Delete link request received: {}", linkParameters);

        DeleteLinkRequest request;
        try {
            request = new DeleteLinkRequest(new SwitchId(linkParameters.getSrcSwitch()), linkParameters.getSrcPort(),
                    new SwitchId(linkParameters.getDstSwitch()), linkParameters.getDstPort());
        } catch (IllegalArgumentException e) {
            logger.error("Could not parse delete link request arguments: {}", e.getMessage());
            throw new MessageException(correlationId, System.currentTimeMillis(), ErrorType.DATA_INVALID,
                    e.getMessage(), "Can not parse arguments when create 'delete link' request");
        }

        CommandMessage message = new CommandMessage(request, System.currentTimeMillis(), correlationId);
        return messagingChannel.sendAndGetChunked(nbworkerTopic, message)
                .thenApply(response -> response.stream()
                        .map(IslInfoData.class::cast)
                        .map(linkMapper::toLinkDto)
                        .collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<List<LinkDto>> updateLinkUnderMaintenance(LinkUnderMaintenanceDto link) {

        final String correlationId = RequestCorrelationId.getId();
        logger.debug("Update under maintenance link request processing");
        UpdateLinkUnderMaintenanceRequest data = null;
        try {
            data = new UpdateLinkUnderMaintenanceRequest(
                    new NetworkEndpoint(new SwitchId(link.getSrcSwitch()), link.getSrcPort()),
                    new NetworkEndpoint(new SwitchId(link.getDstSwitch()), link.getDstPort()),
                    link.isUnderMaintenance(), link.isEvacuate());
        } catch (IllegalArgumentException e) {
            logger.error("Can not parse arguments: {}", e.getMessage());
            throw new MessageException(correlationId, System.currentTimeMillis(), ErrorType.DATA_INVALID,
                    e.getMessage(), "Can not parse arguments when create 'update ISL Under maintenance' request");
        }

        CommandMessage message = new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);
        return messagingChannel.sendAndGetChunked(nbworkerTopic, message)
                .thenApply(response -> response.stream()
                        .map(IslInfoData.class::cast)
                        .map(linkMapper::toLinkDto)
                        .collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<List<LinkDto>> updateLinkEnableBfd(LinkEnableBfdDto link) {

        final String correlationId = RequestCorrelationId.getId();
        logger.debug("Update enable bfd link request processing");
        UpdateLinkEnableBfdRequest data = null;
        try {
            data = new UpdateLinkEnableBfdRequest(
                    new NetworkEndpoint(new SwitchId(link.getSrcSwitch()), link.getSrcPort()),
                    new NetworkEndpoint(new SwitchId(link.getDstSwitch()), link.getDstPort()),
                    link.isEnableBfd());
        } catch (IllegalArgumentException e) {
            logger.error("Can not parse arguments: {}", e.getMessage());
            throw new MessageException(correlationId, System.currentTimeMillis(), ErrorType.DATA_INVALID,
                    e.getMessage(), "Can not parse arguments when create 'update ISL enable bfd' request");
        }

        CommandMessage message = new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);
        return messagingChannel.sendAndGetChunked(nbworkerTopic, message)
                .thenApply(response -> response.stream()
                        .map(IslInfoData.class::cast)
                        .map(linkMapper::toLinkDto)
                        .collect(Collectors.toList()));
    }
}
