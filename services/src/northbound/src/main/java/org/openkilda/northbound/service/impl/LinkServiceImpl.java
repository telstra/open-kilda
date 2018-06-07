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

import static java.util.Base64.getEncoder;
import static org.openkilda.messaging.Utils.CORRELATION_ID;

import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.model.NetworkEndpointMask;
import org.openkilda.messaging.nbtopology.request.GetLinksRequest;
import org.openkilda.messaging.nbtopology.request.LinkPropsGet;
import org.openkilda.messaging.nbtopology.response.LinkPropsData;
import org.openkilda.messaging.te.response.LinkPropsResponse;
import org.openkilda.northbound.converter.LinkMapper;
import org.openkilda.northbound.converter.LinkPropsMapper;
import org.openkilda.northbound.dto.LinkPropsDto;
import org.openkilda.northbound.dto.LinksDto;
import org.openkilda.northbound.messaging.MessageProducer;
import org.openkilda.northbound.service.LinkPropsResult;
import org.openkilda.northbound.service.LinkService;
import org.openkilda.northbound.utils.RequestCorrelationId;
import org.openkilda.northbound.utils.ResponseCollector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class LinkServiceImpl implements LinkService {

    private static final Logger logger = LoggerFactory.getLogger(LinkServiceImpl.class);

    //todo: refactor to use interceptor or custom rest template
    private static final String auth = "kilda:kilda";
    private static final String authHeaderValue = "Basic " + getEncoder().encodeToString(auth.getBytes());

    /** The URL to hit the Links Properties table .. ie :link_props in Neo4J */
    private String linkPropsUrlBase;
    private UriComponentsBuilder linkPropsBuilder;

    @Value("${topology.engine.rest.endpoint}")
    private String topologyEngineRest;

    @Autowired
    private LinkMapper linkMapper;

    @Autowired
    private LinkPropsMapper linkPropsMapper;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${kafka.topo.eng.topic}")
    private String topologyEngineTopic;

    @Value("${kafka.nbworker.topic}")
    private String nbworkerTopic;

    /**
     * Kafka message producer.
     */
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
        return doLinkProps(HttpMethod.PUT, linkPropsList);
    }

    @Override
    public LinkPropsResult delLinkProps(List<LinkPropsDto> linkPropsList) {
        return doLinkProps(HttpMethod.DELETE, linkPropsList);
    }

    protected LinkPropsResult doLinkProps(HttpMethod verb, List<LinkPropsDto> linkPropsList) {
        logger.debug("{} link properties request received", verb);
        logger.debug("Size of list: {}", linkPropsList.size());
        HttpEntity<List<LinkPropsDto>> entity = new HttpEntity<>(linkPropsList, buildHttpHeaders());
        ResponseEntity<LinkPropsResult> response = restTemplate.exchange(linkPropsUrlBase,
                verb, entity, LinkPropsResult.class);
        LinkPropsResult result = response.getBody();
        logger.debug("Returned: ", result);
        return result;
    }

    private HttpHeaders buildHttpHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, authHeaderValue);
        headers.add(CORRELATION_ID, RequestCorrelationId.getId());
        return headers;
    }
}
