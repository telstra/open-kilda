package org.openkilda.northbound.service.impl;

import static java.util.Base64.getEncoder;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.CommandWithReplyToMessage;
import org.openkilda.messaging.command.switches.*;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.switches.ConnectModeResponse;
import org.openkilda.messaging.info.switches.SwitchRulesResponse;
import org.openkilda.northbound.converter.SwitchMapper;
import org.openkilda.northbound.dto.SwitchDto;
import org.openkilda.northbound.messaging.MessageConsumer;
import org.openkilda.northbound.messaging.MessageProducer;
import org.openkilda.northbound.service.SwitchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
    //todo: refactor to use interceptor or custom rest template
    private static final String auth = "kilda:kilda";
    private static final String authHeaderValue = "Basic " + getEncoder().encodeToString(auth.getBytes());

    private String switchesUrl;
    private HttpHeaders headers;

    /**
     * The kafka topic.
     */
    @Value("${topology.engine.rest.endpoint}")
    private String topologyEngineRest;

    @Autowired
    private SwitchMapper switchMapper;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private MessageConsumer messageConsumer;

    @Autowired
    private MessageProducer messageProducer;

    @Value("${kafka.speaker.topic}")
    private String floodlightTopic;

    @Value("${kafka.northbound.topic}")
    private String northboundTopic;

    @PostConstruct
    void init() {
        switchesUrl = UriComponentsBuilder
                .fromHttpUrl(topologyEngineRest)
                .pathSegment("api", "v1", "topology", "switches")
                .build()
                .toUriString();

        headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, authHeaderValue);
    }

    @Override
    public List<SwitchDto> getSwitches() {
        LOGGER.debug("Get switch request received");

        SwitchInfoData[] switches;
        try {
            switches = restTemplate.exchange(switchesUrl, HttpMethod.GET, new HttpEntity<>(headers),
                    SwitchInfoData[].class).getBody();
            LOGGER.debug("Returned {} links", switches.length);
        } catch (RestClientException e) {
            LOGGER.error("Exception during getting switches from TPE", e);
            throw e;
        }

        return Arrays.stream(switches)
                .map(switchMapper::toSwitchDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<Long> deleteRules(String switchId, DeleteRulesAction deleteAction, Long cookie, String correlationId) {
        LOGGER.debug("Delete switch rules request received");

        // TODO: remove  messageConsumer.clear() as a part of NB cleanup (clear isn't required)
        messageConsumer.clear();

        SwitchRulesDeleteRequest data = new SwitchRulesDeleteRequest(switchId, deleteAction, cookie);
        CommandMessage request = new CommandWithReplyToMessage(data, System.currentTimeMillis(), correlationId,
                Destination.CONTROLLER, northboundTopic);
        messageProducer.send(floodlightTopic, request);

        Message message = (Message) messageConsumer.poll(correlationId);
        SwitchRulesResponse response = (SwitchRulesResponse) validateInfoMessage(request, message, correlationId);
        return response.getRuleIds();
    }


    @Override
    public List<Long> installRules(String switchId, InstallRulesAction installAction, String correlationId) {
        LOGGER.debug("Install switch rules request received");

        SwitchRulesInstallRequest data = new SwitchRulesInstallRequest(switchId, installAction);
        CommandMessage request = new CommandWithReplyToMessage(data, System.currentTimeMillis(), correlationId,
                Destination.CONTROLLER, northboundTopic);
        messageProducer.send(floodlightTopic, request);

        Message message = (Message) messageConsumer.poll(correlationId);
        SwitchRulesResponse response = (SwitchRulesResponse) validateInfoMessage(request, message, correlationId);
        return response.getRuleIds();
    }


    @Override
    public ConnectModeRequest.Mode connectMode(ConnectModeRequest.Mode mode, String correlationId) {
        LOGGER.debug("Set/Get switch connect mode request received: mode = {}", mode);

        ConnectModeRequest data = new ConnectModeRequest(mode);
        CommandMessage request = new CommandWithReplyToMessage(data, System.currentTimeMillis(), correlationId,
                Destination.CONTROLLER, northboundTopic);
        messageProducer.send(floodlightTopic, request);

        Message message = (Message) messageConsumer.poll(correlationId);
        ConnectModeResponse response = (ConnectModeResponse) validateInfoMessage(request, message, correlationId);
        return response.getMode();
    }

}
