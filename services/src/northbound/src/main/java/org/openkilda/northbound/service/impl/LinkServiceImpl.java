package org.openkilda.northbound.service.impl;

import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.northbound.converter.LinkMapper;
import org.openkilda.northbound.dto.LinksDto;
import org.openkilda.northbound.service.LinkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.PostConstruct;

@Service
public class LinkServiceImpl implements LinkService {

    private final Logger LOGGER = LoggerFactory.getLogger(LinkServiceImpl.class);

    private String linksUrl;

    /**
     * The kafka topic.
     */
    @Value("${topology.engine.rest.endpoint}")
    private String topologyEngineRest;

    @Autowired
    private LinkMapper linkMapper;

    @Autowired
    private RestTemplate restTemplate;

    @PostConstruct
    void init() {
        linksUrl = UriComponentsBuilder
                .fromHttpUrl(topologyEngineRest)
                .pathSegment("api", "v1", "topology", "links")
                .build()
                .toUriString();
    }

    @Override
    public List<LinksDto> getLinks() {
        LOGGER.debug("Get links request received");
        IslInfoData[] links = restTemplate.getForEntity(linksUrl, IslInfoData[].class).getBody();
        LOGGER.debug("Returned {} links", links.length);
        return Stream.of(links)
                .map(linkMapper::toLinkDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<LinksDto> getLinksBySwitch(String switchId) {
        return null;
    }
}
