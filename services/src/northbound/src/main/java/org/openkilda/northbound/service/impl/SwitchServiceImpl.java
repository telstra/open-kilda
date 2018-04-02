package org.openkilda.northbound.service.impl;

import static java.util.Base64.getEncoder;

import org.openkilda.client.response.switches.SyncRulesOutput;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.CommandWithReplyToMessage;
import org.openkilda.messaging.command.switches.ConnectModeRequest;
import org.openkilda.messaging.command.switches.DeleteRulesAction;
import org.openkilda.messaging.command.switches.InstallRulesAction;
import org.openkilda.messaging.command.switches.SwitchRulesDeleteRequest;
import org.openkilda.messaging.command.switches.DumpRulesRequest;
import org.openkilda.messaging.command.switches.SwitchRulesInstallRequest;
import org.openkilda.messaging.command.switches.SyncRulesRequest;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.messaging.info.switches.ConnectModeResponse;
import org.openkilda.messaging.info.switches.SwitchRulesResponse;
import org.openkilda.messaging.info.switches.SyncRulesResponse;
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

import java.util.ArrayList;
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

    @Value("${topology.engine.rest.endpoint}")
    private String topologyEngineRest;

    @Value("${kafka.topo.eng.topic}")
    private String topoEngTopic;

    @Autowired
    private MessageProducer messageProducer;

    @Autowired
    private MessageConsumer<Message> messageConsumer;

    @Autowired
    private SwitchMapper switchMapper;

    @Autowired
    private RestTemplate restTemplate;

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

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
    @Override
    public SwitchFlowEntries getRules(String switchId, Long cookie, String correlationId) {

        DumpRulesRequest request = new DumpRulesRequest(switchId);
        CommandWithReplyToMessage commandMessage = new CommandWithReplyToMessage(request, System.currentTimeMillis(),
                correlationId, Destination.CONTROLLER, northboundTopic);
        messageProducer.send(floodlightTopic, commandMessage);
        Message message = messageConsumer.poll(correlationId);
        SwitchFlowEntries response = (SwitchFlowEntries) validateInfoMessage(commandMessage, message, correlationId);

        if (cookie > 0L) {
            List<FlowEntry> matchedFlows = new ArrayList<>();
            for (FlowEntry entry : response.getFlowEntries()){
                if (cookie.equals(entry.getCookie())){
                    matchedFlows.add(entry);
                }
            }
            response = new SwitchFlowEntries(response.getSwitchId(), matchedFlows);
        }
        return response;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Long> deleteRules(String switchId, DeleteRulesAction deleteAction, Long cookie, String correlationId) {
        LOGGER.debug("Delete switch rules request received");

        SwitchRulesDeleteRequest data = new SwitchRulesDeleteRequest(switchId, deleteAction, cookie);
        CommandMessage request = new CommandWithReplyToMessage(data, System.currentTimeMillis(), correlationId,
                Destination.CONTROLLER, northboundTopic);
        messageProducer.send(floodlightTopic, request);

        Message message = messageConsumer.poll(correlationId);
        SwitchRulesResponse response = (SwitchRulesResponse) validateInfoMessage(request, message, correlationId);
        return response.getRuleIds();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public List<Long> installRules(String switchId, InstallRulesAction installAction, String correlationId) {
        LOGGER.debug("Install switch rules request received");

        SwitchRulesInstallRequest data = new SwitchRulesInstallRequest(switchId, installAction);
        CommandMessage request = new CommandWithReplyToMessage(data, System.currentTimeMillis(), correlationId,
                Destination.CONTROLLER, northboundTopic);
        messageProducer.send(floodlightTopic, request);

        Message message = messageConsumer.poll(correlationId);
        SwitchRulesResponse response = (SwitchRulesResponse) validateInfoMessage(request, message, correlationId);
        return response.getRuleIds();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public ConnectModeRequest.Mode connectMode(ConnectModeRequest.Mode mode, String correlationId) {
        LOGGER.debug("Set/Get switch connect mode request received: mode = {}", mode);

        ConnectModeRequest data = new ConnectModeRequest(mode);
        CommandMessage request = new CommandWithReplyToMessage(data, System.currentTimeMillis(), correlationId,
                Destination.CONTROLLER, northboundTopic);
        messageProducer.send(floodlightTopic, request);

        Message message = messageConsumer.poll(correlationId);
        ConnectModeResponse response = (ConnectModeResponse) validateInfoMessage(request, message, correlationId);
        return response.getMode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SyncRulesOutput syncRules(String switchId, String correlationId) {
        SyncRulesRequest request = new SyncRulesRequest(switchId);
        CommandWithReplyToMessage commandMessage = new CommandWithReplyToMessage(request, System.currentTimeMillis(),
                correlationId, Destination.TOPOLOGY_ENGINE, northboundTopic);
        messageProducer.send(topoEngTopic, commandMessage);

        Message message = messageConsumer.poll(correlationId);
        SyncRulesResponse response = (SyncRulesResponse) validateInfoMessage(commandMessage, message, correlationId);
        return switchMapper.toSuncRulesOutput(response);
    }
}
