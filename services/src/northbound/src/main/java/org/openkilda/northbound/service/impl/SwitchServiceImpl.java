package org.openkilda.northbound.service.impl;

import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.northbound.converter.SwitchMapper;
import org.openkilda.northbound.dto.SwitchDto;
import org.openkilda.northbound.service.SwitchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;

@Service
public class SwitchServiceImpl implements SwitchService {

    private final Logger LOGGER = LoggerFactory.getLogger(LinkServiceImpl.class);

    private String switchesUrl;

    /**
     * The kafka topic.
     */
    @Value("${topology.engine.rest.endpoint}")
    private String topologyEngineRest;

    @Autowired
    private SwitchMapper switchMapper;

    @Autowired
    private RestTemplate restTemplate;

    @PostConstruct
    void init() {
        switchesUrl = UriComponentsBuilder
                .fromHttpUrl(topologyEngineRest)
                .pathSegment("api", "v1", "topology", "switches")
                .build()
                .toUriString();
    }

    @Override
    public List<SwitchDto> getSwitches() {
        LOGGER.debug("Get switch request received");

        SwitchInfoData[] switches;
        try {
            switches = restTemplate.getForEntity(switchesUrl, SwitchInfoData[].class).getBody();
            LOGGER.debug("Returned {} links", switches.length);
        } catch (RestClientException e) {
            LOGGER.error("Exception during getting switches from TPE", e);
            throw e;
        }

        return Arrays.stream(switches)
                .map(switchMapper::toSwitchDto)
                .collect(Collectors.toList());
    }

}
