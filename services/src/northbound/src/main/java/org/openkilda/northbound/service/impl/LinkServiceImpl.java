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

import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.model.NetworkEndpointMask;
import org.openkilda.messaging.nbtopology.request.GetLinksRequest;
import org.openkilda.messaging.nbtopology.request.LinkPropsGet;
import org.openkilda.messaging.nbtopology.response.LinkPropsData;
import org.openkilda.messaging.te.request.LinkPropsDrop;
import org.openkilda.messaging.te.request.LinkPropsPut;
import org.openkilda.messaging.te.response.LinkPropsResponse;
import org.openkilda.northbound.converter.LinkMapper;
import org.openkilda.northbound.converter.LinkPropsMapper;
import org.openkilda.northbound.dto.LinkPropsDto;
import org.openkilda.northbound.dto.LinksDto;
import org.openkilda.northbound.messaging.MessageConsumer;
import org.openkilda.northbound.messaging.MessageProducer;
import org.openkilda.northbound.service.LinkPropsResult;
import org.openkilda.northbound.service.LinkService;
import org.openkilda.northbound.utils.CorrelationIdFactory;
import org.openkilda.northbound.utils.RequestCorrelationId;
import org.openkilda.northbound.utils.ResponseCollector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
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
    private MessageConsumer messageConsumer;
    @Autowired
    private MessageProducer messageProducer;

    @Autowired
    private ResponseCollector<IslInfoData> linksCollector;
    @Autowired
    private ResponseCollector<LinkPropsResponse> teLinksCollector;
    @Autowired
    private ResponseCollector<LinkPropsData> linksPropsCollector;

    @Override
    public List<LinksDto> getLinks() {
        final String correlationId = RequestCorrelationId.getId();
        logger.debug("Get links request received");
        CommandMessage request = new CommandMessage(new GetLinksRequest(), System.currentTimeMillis(), correlationId);
        messageProducer.send(nbworkerTopic, request);
        List<IslInfoData> links = linksCollector.getResult(correlationId);

        return links.stream()
                .map(linkMapper::toLinkDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<LinkPropsDto> getLinkProps(String srcSwitch, Integer srcPort, String dstSwitch, Integer dstPort) {
        final String correlationId = RequestCorrelationId.getId();
        logger.debug("Get link properties request received");
        LinkPropsGet request = new LinkPropsGet(new NetworkEndpointMask(srcSwitch, srcPort),
                new NetworkEndpointMask(dstSwitch, dstPort));
        CommandMessage message = new CommandMessage(request, System.currentTimeMillis(), correlationId);
        messageProducer.send(nbworkerTopic, message);
        List<LinkPropsData> links = linksPropsCollector.getResult(correlationId);

        logger.debug("Found link props items: {}", links.size());
        return links.stream()
                .map(linkPropsMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public LinkPropsResult setLinkProps(List<LinkPropsDto> linkPropsList) {
        logger.debug("Link props \"SET\" request received (consists of {} records)", linkPropsList.size());

        ArrayList<String> pendingRequest = new ArrayList<>(linkPropsList.size());
        for (LinkPropsDto requestItem : linkPropsList) {
            LinkPropsPut teRequest = new LinkPropsPut(linkPropsMapper.toLinkProps(requestItem));
            String requestId = idFactory.produceChained(RequestCorrelationId.getId());
            CommandMessage message = new CommandMessage(teRequest, System.currentTimeMillis(), requestId);
            messageProducer.send(topologyEngineTopic, message);

            pendingRequest.add(requestId);
        }

        int successCount = 0;
        ArrayList<String> errors = new ArrayList<>(pendingRequest.size());
        for (String requestId : pendingRequest) {
            InfoMessage message = (InfoMessage) messageConsumer.poll(requestId);
            LinkPropsResponse response = (LinkPropsResponse) message.getData();

            if (response.isSuccess()) {
                successCount += 1;
            } else {
                errors.add(response.getError());
            }
        }

        return new LinkPropsResult(
                linkPropsList.size() - successCount, successCount,
                errors.toArray(new String[0]));
    }

    @Override
    public LinkPropsResult delLinkProps(List<LinkPropsDto> linkPropsList) {
        ArrayList<String> pendingChains = new ArrayList<>();
        for (LinkPropsDto requestItem : linkPropsList) {
            LinkPropsDrop teRequest = new LinkPropsDrop(linkPropsMapper.toLinkPropsMask(requestItem));
            String requestId = idFactory.produceChained(RequestCorrelationId.getId());
            CommandMessage message = new CommandMessage(teRequest, System.currentTimeMillis(), requestId);
            messageProducer.send(topologyEngineTopic, message);

            pendingChains.add(requestId);
        }

        int successCount = 0;
        ArrayList<String> errors = new ArrayList<>();
        for (String requestId : pendingChains) {
            List<LinkPropsResponse> responseBatch = teLinksCollector.getResult(requestId);
            for (LinkPropsResponse response : responseBatch) {
                if (response.isSuccess()) {
                    successCount += 1;
                } else {
                    errors.add(response.getError());
                }
            }
        }

        return new LinkPropsResult(errors.size(), successCount, errors.toArray(new String[0]));
    }
}
