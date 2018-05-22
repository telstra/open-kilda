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

package org.openkilda.northbound.service.impl;

import static java.util.Base64.getEncoder;
import static org.openkilda.messaging.Utils.CORRELATION_ID;

import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.northbound.converter.LinkMapper;
import org.openkilda.northbound.dto.LinkPropsDto;
import org.openkilda.northbound.dto.LinksDto;
import org.openkilda.northbound.service.LinkPropsResult;
import org.openkilda.northbound.service.LinkService;
import org.openkilda.northbound.utils.RequestCorrelationId;
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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.PostConstruct;

@Service
public class LinkServiceImpl implements LinkService {

    //todo: refactor to use interceptor or custom rest template
    private static final String auth = "kilda:kilda";
    private static final String authHeaderValue = "Basic " + getEncoder().encodeToString(auth.getBytes());

    private final Logger LOGGER = LoggerFactory.getLogger(LinkServiceImpl.class);

    private String linksUrl;
    /** The URL to hit the Links Properties table .. ie :link_props in Neo4J */
    private String linkPropsUrlBase;
    private UriComponentsBuilder linkPropsBuilder;

    @Value("${topology.engine.rest.endpoint}")
    private String topologyEngineRest;

    @Autowired
    private LinkMapper linkMapper;

    @Autowired
    private RestTemplate restTemplate;

    @PostConstruct
    void init() {
        linksUrl = UriComponentsBuilder.fromHttpUrl(topologyEngineRest)
                .pathSegment("api", "v1", "topology", "links").build().toUriString();
        linkPropsBuilder = UriComponentsBuilder.fromHttpUrl(topologyEngineRest)
                .pathSegment("api", "v1", "topology", "link", "props");
        linkPropsUrlBase = UriComponentsBuilder.fromHttpUrl(topologyEngineRest)
                .pathSegment("api", "v1", "topology", "link", "props").build().toUriString();
    }

    @Override
    public List<LinksDto> getLinks() {
        LOGGER.debug("Get links request received");
        IslInfoData[] links = restTemplate.exchange(linksUrl, HttpMethod.GET, new HttpEntity<>(buildHttpHeaders()),
                IslInfoData[].class).getBody();
        LOGGER.debug("Returned {} links", links.length);
        return Stream.of(links)
                .map(linkMapper::toLinkDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<LinkPropsDto> getLinkProps(LinkPropsDto keys) {
        LOGGER.debug("Get link properties request received");
        UriComponentsBuilder builder = linkPropsBuilder.cloneBuilder();
        // TODO: pull out the URI builder .. to facilitate unit testing
        if (keys != null){
            if (!keys.getSrc_switch().isEmpty())
                builder.queryParam("src_switch", keys.getSrc_switch());
            if (!keys.getSrc_port().isEmpty())
                builder.queryParam("src_port", keys.getSrc_port());
            if (!keys.getDst_switch().isEmpty())
                builder.queryParam("dst_switch", keys.getDst_switch());
            if (!keys.getDst_port().isEmpty())
                builder.queryParam("dst_port", keys.getDst_port());
        }
        String fullUri = builder.build().toUriString();

        ResponseEntity<LinkPropsDto[]> response = restTemplate.exchange(fullUri,
                HttpMethod.GET, new HttpEntity<>(buildHttpHeaders()), LinkPropsDto[].class);
        LinkPropsDto[] linkProps = response.getBody();
        LOGGER.debug("Returned {} links (with properties)", linkProps.length);
        return Arrays.asList(linkProps);
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
        LOGGER.debug("{} link properties request received", verb);
        LOGGER.debug("Size of list: {}", linkPropsList.size());
        HttpEntity<List<LinkPropsDto>> entity = new HttpEntity<>(linkPropsList, buildHttpHeaders());
        ResponseEntity<LinkPropsResult> response = restTemplate.exchange(linkPropsUrlBase,
                verb, entity, LinkPropsResult.class);
        LinkPropsResult result = response.getBody();
        LOGGER.debug("Returned: ", result);
        return result;
    }

    private HttpHeaders buildHttpHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, authHeaderValue);
        headers.add(CORRELATION_ID, RequestCorrelationId.getId());
        return headers;
    }
}
