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

import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.model.LinkProps;
import org.openkilda.messaging.model.NetworkEndpointMask;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.messaging.nbtopology.request.GetLinksRequest;
import org.openkilda.messaging.nbtopology.request.LinkPropsGet;
import org.openkilda.messaging.nbtopology.response.LinkPropsData;
import org.openkilda.messaging.te.request.LinkPropsDrop;
import org.openkilda.messaging.te.request.LinkPropsPut;
import org.openkilda.messaging.te.response.LinkPropsResponse;
import org.openkilda.northbound.converter.LinkMapper;
import org.openkilda.northbound.converter.LinkPropsMapper;
import org.openkilda.northbound.dto.BatchResults;
import org.openkilda.northbound.dto.links.LinkDto;
import org.openkilda.northbound.dto.links.LinkPropsDto;
import org.openkilda.northbound.messaging.MessagingChannel;
import org.openkilda.northbound.service.LinkService;
import org.openkilda.northbound.utils.CorrelationIdFactory;
import org.openkilda.northbound.utils.RequestCorrelationId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
    private LinkPropsMapper linkPropsMapper;

    @Value("#{kafkaTopicsConfig.getTopoEngTopic()}")
    private String topologyEngineTopic;

    /**
     * The kafka topic for the nb topology.
     */
    @Value("#{kafkaTopicsConfig.getTopoNbTopic()}")
    private String nbworkerTopic;

    @Autowired
    private MessagingChannel messagingChannel;

    @Override
    public CompletableFuture<List<LinkDto>> getLinks() {
        final String correlationId = RequestCorrelationId.getId();
        logger.debug("Get links request received");
        CommandMessage request = new CommandMessage(new GetLinksRequest(), System.currentTimeMillis(), correlationId);

        return messagingChannel.sendAndGetChunked(nbworkerTopic, request)
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
            LinkProps linkProps;
            try {
                linkProps = linkPropsMapper.toLinkProps(requestItem);
            } catch (IllegalArgumentException e) {
                errors.add(e.getMessage());
                continue;
            }
            LinkPropsPut teRequest = new LinkPropsPut(linkProps);
            String requestId = idFactory.produceChained(RequestCorrelationId.getId());
            CommandMessage message = new CommandMessage(teRequest, System.currentTimeMillis(), requestId);

            pendingRequest.add(messagingChannel.sendAndGet(topologyEngineTopic, message));
        }

        return collectResponses(pendingRequest, LinkPropsResponse.class)
                .thenApply(responses -> getLinkPropsResult(responses, errors));
    }

    @Override
    public CompletableFuture<BatchResults> delLinkProps(List<LinkPropsDto> linkPropsList) {
        List<CompletableFuture<List<InfoData>>> pendingRequest = new ArrayList<>(linkPropsList.size());

        for (LinkPropsDto requestItem : linkPropsList) {
            LinkPropsDrop teRequest = new LinkPropsDrop(linkPropsMapper.toLinkPropsMask(requestItem));
            String requestId = idFactory.produceChained(RequestCorrelationId.getId());
            CommandMessage message = new CommandMessage(teRequest, System.currentTimeMillis(), requestId);

            pendingRequest.add(messagingChannel.sendAndGetChunked(topologyEngineTopic, message));
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
}
