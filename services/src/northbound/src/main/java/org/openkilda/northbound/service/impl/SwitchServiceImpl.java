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

import static java.lang.String.format;
import static java.util.Collections.emptyList;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.CommandWithReplyToMessage;
import org.openkilda.messaging.command.flow.DeleteMeterRequest;
import org.openkilda.messaging.command.switches.ConnectModeRequest;
import org.openkilda.messaging.command.switches.DeleteRulesAction;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria;
import org.openkilda.messaging.command.switches.DumpRulesRequest;
import org.openkilda.messaging.command.switches.InstallRulesAction;
import org.openkilda.messaging.command.switches.PortConfigurationRequest;
import org.openkilda.messaging.command.switches.PortStatus;
import org.openkilda.messaging.command.switches.SwitchRulesDeleteRequest;
import org.openkilda.messaging.command.switches.SwitchRulesInstallRequest;
import org.openkilda.messaging.command.switches.SwitchRulesSyncRequest;
import org.openkilda.messaging.command.switches.SwitchRulesValidateRequest;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.messaging.info.switches.ConnectModeResponse;
import org.openkilda.messaging.info.switches.DeleteMeterResponse;
import org.openkilda.messaging.info.switches.PortConfigurationResponse;
import org.openkilda.messaging.info.switches.SwitchRulesResponse;
import org.openkilda.messaging.info.switches.SyncRulesResponse;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.messaging.nbtopology.request.GetSwitchesRequest;
import org.openkilda.messaging.payload.switches.PortConfigurationPayload;
import org.openkilda.northbound.converter.SwitchMapper;
import org.openkilda.northbound.dto.switches.DeleteMeterResult;
import org.openkilda.northbound.dto.switches.PortDto;
import org.openkilda.northbound.dto.switches.RulesSyncResult;
import org.openkilda.northbound.dto.switches.RulesValidationResult;
import org.openkilda.northbound.dto.switches.SwitchDto;
import org.openkilda.northbound.messaging.MessageConsumer;
import org.openkilda.northbound.messaging.MessageProducer;
import org.openkilda.northbound.service.SwitchService;
import org.openkilda.northbound.utils.RequestCorrelationId;
import org.openkilda.northbound.utils.ResponseCollector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SwitchServiceImpl implements SwitchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SwitchServiceImpl.class);

    @Value("#{kafkaTopicsConfig.getTopoEngTopic()}")
    private String topoEngTopic;

    @Autowired
    private MessageProducer messageProducer;

    @Autowired
    private MessageConsumer<Message> messageConsumer;

    @Autowired
    private ResponseCollector<SwitchInfoData> switchesCollector;

    @Autowired
    private SwitchMapper switchMapper;

    @Value("#{kafkaTopicsConfig.getSpeakerTopic()}")
    private String floodlightTopic;

    @Value("#{kafkaTopicsConfig.getNorthboundTopic()}")
    private String northboundTopic;

    @Value("#{kafkaTopicsConfig.getTopoNbTopic()}")
    private String nbworkerTopic;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SwitchDto> getSwitches() {
        final String correlationId = RequestCorrelationId.getId();
        LOGGER.debug("Get switch request received");
        CommandMessage request = new CommandMessage(new GetSwitchesRequest(), System.currentTimeMillis(),
                correlationId);
        messageProducer.send(nbworkerTopic, request);

        List<SwitchInfoData> switches = switchesCollector.getResult(correlationId);
        return switches.stream()
                .map(switchMapper::toSwitchDto)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SwitchFlowEntries getRules(SwitchId switchId, Long cookie, String correlationId) {
        DumpRulesRequest request = new DumpRulesRequest(switchId);
        CommandWithReplyToMessage commandMessage = new CommandWithReplyToMessage(request, System.currentTimeMillis(),
                correlationId, Destination.CONTROLLER, northboundTopic);
        messageProducer.send(floodlightTopic, commandMessage);
        Message message = messageConsumer.poll(correlationId);
        SwitchFlowEntries response = (SwitchFlowEntries) validateInfoMessage(commandMessage, message, correlationId);

        if (cookie > 0L) {
            List<FlowEntry> matchedFlows = new ArrayList<>();
            for (FlowEntry entry : response.getFlowEntries()) {
                if (cookie.equals(entry.getCookie())) {
                    matchedFlows.add(entry);
                }
            }
            response = new SwitchFlowEntries(response.getSwitchId(), matchedFlows);
        }
        return response;
    }

    @Override
    public SwitchFlowEntries getRules(SwitchId switchId, Long cookie) {
        return getRules(switchId, cookie, RequestCorrelationId.getId());
    }

    @Override
    public List<Long> deleteRules(SwitchId switchId, DeleteRulesAction deleteAction) {
        final String correlationId = RequestCorrelationId.getId();
        LOGGER.debug("Delete switch rules request received: deleteAction={}", deleteAction);

        SwitchRulesDeleteRequest data = new SwitchRulesDeleteRequest(switchId, deleteAction, null);
        CommandMessage request = new CommandWithReplyToMessage(data, System.currentTimeMillis(), correlationId,
                Destination.CONTROLLER, northboundTopic);
        messageProducer.send(floodlightTopic, request);

        Message message = messageConsumer.poll(correlationId);
        SwitchRulesResponse response = (SwitchRulesResponse) validateInfoMessage(request, message, correlationId);
        return response.getRuleIds();
    }

    @Override
    public List<Long> deleteRules(SwitchId switchId, DeleteRulesCriteria criteria) {
        final String correlationId = RequestCorrelationId.getId();
        LOGGER.debug("Delete switch rules request received: criteria={}", criteria);

        SwitchRulesDeleteRequest data = new SwitchRulesDeleteRequest(switchId, null, criteria);
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
    public List<Long> installRules(SwitchId switchId, InstallRulesAction installAction) {
        final String correlationId = RequestCorrelationId.getId();
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
    public ConnectModeRequest.Mode connectMode(ConnectModeRequest.Mode mode) {
        final String correlationId = RequestCorrelationId.getId();
        LOGGER.debug("Set/Get switch connect mode request received: mode = {}", mode);

        ConnectModeRequest data = new ConnectModeRequest(mode);
        CommandMessage request = new CommandWithReplyToMessage(data, System.currentTimeMillis(), correlationId,
                Destination.CONTROLLER, northboundTopic);
        messageProducer.send(floodlightTopic, request);

        Message message = messageConsumer.poll(correlationId);
        ConnectModeResponse response = (ConnectModeResponse) validateInfoMessage(request, message, correlationId);
        return response.getMode();
    }

    @Override
    public RulesValidationResult validateRules(SwitchId switchId) {
        final String correlationId = RequestCorrelationId.getId();

        CommandWithReplyToMessage validateCommandMessage = new CommandWithReplyToMessage(
                new SwitchRulesValidateRequest(switchId),
                System.currentTimeMillis(), correlationId, Destination.TOPOLOGY_ENGINE, northboundTopic);
        messageProducer.send(topoEngTopic, validateCommandMessage);

        Message validateResponseMessage = messageConsumer.poll(correlationId);
        SyncRulesResponse validateResponse = (SyncRulesResponse) validateInfoMessage(validateCommandMessage,
                validateResponseMessage, correlationId);

        return switchMapper.toRulesValidationResult(validateResponse);
    }

    @Override
    public RulesSyncResult syncRules(SwitchId switchId) {
        RulesValidationResult validationResult = validateRules(switchId);
        List<Long> missingRules = validationResult.getMissingRules();

        if (CollectionUtils.isEmpty(missingRules)) {
            return switchMapper.toRulesSyncResult(validationResult, emptyList());
        }

        LOGGER.debug("The validation result for switch {}: missing rules = {}", switchId, missingRules);

        // Synchronize the missing rules
        String syncCorrelationId = format("%s-sync", RequestCorrelationId.getId());
        CommandWithReplyToMessage syncCommandMessage = new CommandWithReplyToMessage(
                new SwitchRulesSyncRequest(switchId, missingRules),
                System.currentTimeMillis(), syncCorrelationId, Destination.TOPOLOGY_ENGINE, northboundTopic);
        messageProducer.send(topoEngTopic, syncCommandMessage);

        Message syncResponseMessage = messageConsumer.poll(syncCorrelationId);
        SyncRulesResponse syncResponse = (SyncRulesResponse) validateInfoMessage(syncCommandMessage,
                syncResponseMessage, syncCorrelationId);

        return switchMapper.toRulesSyncResult(validationResult, syncResponse.getInstalledRules());
    }

    @Override
    public DeleteMeterResult deleteMeter(SwitchId switchId, long meterId) {
        String requestId = RequestCorrelationId.getId();
        CommandWithReplyToMessage deleteCommand = new CommandWithReplyToMessage(
                new DeleteMeterRequest(switchId, meterId),
                System.currentTimeMillis(), requestId, Destination.TOPOLOGY_ENGINE, northboundTopic);
        messageProducer.send(floodlightTopic, deleteCommand);

        Message response = messageConsumer.poll(requestId);
        DeleteMeterResponse result = (DeleteMeterResponse) validateInfoMessage(deleteCommand, response, requestId);
        return new DeleteMeterResult(result.isDeleted());
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public PortDto configurePort(SwitchId switchId,  int port, PortConfigurationPayload config) {
        String correlationId = RequestCorrelationId.getId();

        PortConfigurationRequest request = new PortConfigurationRequest(switchId, 
                port, toPortAdminDown(config.getStatus()));
        CommandWithReplyToMessage updateStatusCommand = new CommandWithReplyToMessage(
                request, System.currentTimeMillis(), correlationId, 
                Destination.CONTROLLER, northboundTopic);
        messageProducer.send(floodlightTopic, updateStatusCommand);

        Message response = messageConsumer.poll(correlationId);
        PortConfigurationResponse switchPortResponse = (PortConfigurationResponse) validateInfoMessage(
                updateStatusCommand, response, correlationId);

        return new PortDto(switchPortResponse.getSwitchId().toString(), switchPortResponse.getPortNo());
    }

    private Boolean toPortAdminDown(PortStatus status) {
        if (status == null) {
            return  null;
        }

        boolean adminDownState;
        switch (status) {
            case UP:
                adminDownState = false;
                break;
            case DOWN:
                adminDownState = true;
                break;
            default:
                throw new IllegalArgumentException(String.format(
                        "Unsupported enum %s value: %s", PortStatus.class.getName(), status));
        }
        return adminDownState;
    }
}
