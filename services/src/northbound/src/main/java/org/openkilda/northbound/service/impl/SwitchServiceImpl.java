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

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.DeleteMeterRequest;
import org.openkilda.messaging.command.switches.ConnectModeRequest;
import org.openkilda.messaging.command.switches.DeleteRulesAction;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria;
import org.openkilda.messaging.command.switches.DumpMetersRequest;
import org.openkilda.messaging.command.switches.DumpPortDescriptionRequest;
import org.openkilda.messaging.command.switches.DumpRulesRequest;
import org.openkilda.messaging.command.switches.DumpSwitchPortsDescriptionRequest;
import org.openkilda.messaging.command.switches.InstallRulesAction;
import org.openkilda.messaging.command.switches.PortConfigurationRequest;
import org.openkilda.messaging.command.switches.SwitchRulesDeleteRequest;
import org.openkilda.messaging.command.switches.SwitchRulesInstallRequest;
import org.openkilda.messaging.command.switches.SwitchRulesSyncRequest;
import org.openkilda.messaging.command.switches.ValidateRulesRequest;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.meter.SwitchMeterEntries;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.messaging.info.switches.ConnectModeResponse;
import org.openkilda.messaging.info.switches.DeleteMeterResponse;
import org.openkilda.messaging.info.switches.PortConfigurationResponse;
import org.openkilda.messaging.info.switches.PortDescription;
import org.openkilda.messaging.info.switches.SwitchPortsDescription;
import org.openkilda.messaging.info.switches.SwitchRulesResponse;
import org.openkilda.messaging.info.switches.SyncRulesResponse;
import org.openkilda.messaging.nbtopology.request.DeleteSwitchRequest;
import org.openkilda.messaging.nbtopology.request.GetSwitchRequest;
import org.openkilda.messaging.nbtopology.request.GetSwitchesRequest;
import org.openkilda.messaging.nbtopology.request.UpdateSwitchUnderMaintenanceRequest;
import org.openkilda.messaging.nbtopology.response.DeleteSwitchResponse;
import org.openkilda.messaging.payload.switches.PortConfigurationPayload;
import org.openkilda.model.PortStatus;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.converter.SwitchMapper;
import org.openkilda.northbound.dto.switches.DeleteMeterResult;
import org.openkilda.northbound.dto.switches.DeleteSwitchResult;
import org.openkilda.northbound.dto.switches.PortDto;
import org.openkilda.northbound.dto.switches.RulesSyncResult;
import org.openkilda.northbound.dto.switches.RulesValidationResult;
import org.openkilda.northbound.dto.switches.SwitchDto;
import org.openkilda.northbound.dto.switches.UnderMaintenanceDto;
import org.openkilda.northbound.messaging.MessagingChannel;
import org.openkilda.northbound.service.SwitchService;
import org.openkilda.northbound.utils.RequestCorrelationId;

import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class SwitchServiceImpl implements SwitchService {

    private static final Logger logger = LoggerFactory.getLogger(SwitchServiceImpl.class);

    @Autowired
    private MessagingChannel messagingChannel;

    @Autowired
    private SwitchMapper switchMapper;

    @Value("#{kafkaTopicsConfig.getSpeakerTopic()}")
    private String floodlightTopic;

    @Value("#{kafkaTopicsConfig.getNorthboundTopic()}")
    private String northboundTopic;

    @Value("#{kafkaTopicsConfig.getTopoNbTopic()}")
    private String nbworkerTopic;

    @Value("#{kafkaTopicsConfig.getTopoSwitchManagerTopic()}")
    private String switchManagerTopic;

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<List<SwitchDto>> getSwitches() {
        final String correlationId = RequestCorrelationId.getId();
        logger.debug("Get switch request received");
        CommandMessage request = new CommandMessage(new GetSwitchesRequest(), System.currentTimeMillis(),
                correlationId);

        return messagingChannel.sendAndGetChunked(nbworkerTopic, request)
                .thenApply(messages -> messages.stream()
                        .map(SwitchInfoData.class::cast)
                        .map(switchMapper::toSwitchDto)
                        .collect(Collectors.toList()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<SwitchDto> getSwitch(SwitchId switchId) {
        logger.debug("Get one switch request");
        CommandMessage request = new CommandMessage(new GetSwitchRequest(switchId), System.currentTimeMillis(),
                RequestCorrelationId.getId());

        return messagingChannel.sendAndGetChunked(nbworkerTopic, request)
                .thenApply(messages -> messages.stream()
                        .map(SwitchInfoData.class::cast)
                        .map(switchMapper::toSwitchDto)
                        .collect(Collectors.toList()).get(0));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<SwitchFlowEntries> getRules(SwitchId switchId, Long cookie, String correlationId) {
        DumpRulesRequest request = new DumpRulesRequest(switchId);
        CommandMessage commandMessage = new CommandMessage(request, System.currentTimeMillis(),
                correlationId);

        return messagingChannel.sendAndGet(floodlightTopic, commandMessage)
                .thenApply(SwitchFlowEntries.class::cast)
                .thenApply(data -> cookie > NumberUtils.LONG_ZERO ? findByCookie(cookie, data) : data);
    }

    @Override
    public CompletableFuture<SwitchFlowEntries> getRules(SwitchId switchId, Long cookie) {
        return getRules(switchId, cookie, RequestCorrelationId.getId());
    }

    @Override
    public CompletableFuture<List<Long>> deleteRules(SwitchId switchId, DeleteRulesAction deleteAction) {
        final String correlationId = RequestCorrelationId.getId();
        logger.info("Delete switch rules request received: switch={}, deleteAction={}", switchId, deleteAction);

        SwitchRulesDeleteRequest data = new SwitchRulesDeleteRequest(switchId, deleteAction, null);
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId);

        return messagingChannel.sendAndGet(floodlightTopic, request)
                .thenApply(SwitchRulesResponse.class::cast)
                .thenApply(SwitchRulesResponse::getRuleIds);
    }

    @Override
    public CompletableFuture<List<Long>> deleteRules(SwitchId switchId, DeleteRulesCriteria criteria) {
        final String correlationId = RequestCorrelationId.getId();
        logger.info("Delete switch rules request received: switch={}, criteria={}", switchId, criteria);

        SwitchRulesDeleteRequest data = new SwitchRulesDeleteRequest(switchId, null, criteria);
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId);

        return messagingChannel.sendAndGet(floodlightTopic, request)
                .thenApply(SwitchRulesResponse.class::cast)
                .thenApply(SwitchRulesResponse::getRuleIds);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<List<Long>> installRules(SwitchId switchId, InstallRulesAction installAction) {
        final String correlationId = RequestCorrelationId.getId();
        logger.info("Install switch rules request received: switch={}, action={}", switchId, installAction);

        SwitchRulesInstallRequest data = new SwitchRulesInstallRequest(switchId, installAction);
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId);

        return messagingChannel.sendAndGet(floodlightTopic, request)
                .thenApply(SwitchRulesResponse.class::cast)
                .thenApply(SwitchRulesResponse::getRuleIds);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<ConnectModeRequest.Mode> connectMode(ConnectModeRequest.Mode mode) {
        final String correlationId = RequestCorrelationId.getId();
        logger.debug("Set/Get switch connect mode request received: mode = {}", mode);

        ConnectModeRequest data = new ConnectModeRequest(mode);
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId);

        return messagingChannel.sendAndGet(floodlightTopic, request)
                .thenApply(ConnectModeResponse.class::cast)
                .thenApply(ConnectModeResponse::getMode);
    }

    @Override
    public CompletableFuture<RulesValidationResult> validateRules(SwitchId switchId) {
        final String correlationId = RequestCorrelationId.getId();

        CommandMessage validateCommandMessage = new CommandMessage(
                new ValidateRulesRequest(switchId), System.currentTimeMillis(), correlationId);

        return messagingChannel.sendAndGet(floodlightTopic, validateCommandMessage)
                .thenApply(SyncRulesResponse.class::cast)
                .thenApply(switchMapper::toRulesValidationResult);
    }

    @Override
    public CompletableFuture<RulesSyncResult> syncRules(SwitchId switchId) {
        logger.info("Sync rules request for switch {}", switchId);

        CommandMessage syncCommandMessage = new CommandMessage(
                new SwitchRulesSyncRequest(switchId), System.currentTimeMillis(), RequestCorrelationId.getId());

        return messagingChannel.sendAndGet(switchManagerTopic, syncCommandMessage)
                .thenApply(SyncRulesResponse.class::cast)
                .thenApply(switchMapper::toRulesSyncResult);
    }

    @Override
    public CompletableFuture<SwitchMeterEntries> getMeters(SwitchId switchId) {
        String requestId = RequestCorrelationId.getId();
        CommandMessage dumpCommand = new CommandMessage(
                new DumpMetersRequest(switchId), System.currentTimeMillis(), requestId);
        return messagingChannel.sendAndGet(floodlightTopic, dumpCommand)
                .thenApply(SwitchMeterEntries.class::cast);
    }

    @Override
    public CompletableFuture<DeleteMeterResult> deleteMeter(SwitchId switchId, long meterId) {
        String requestId = RequestCorrelationId.getId();
        CommandMessage deleteCommand = new CommandMessage(
                new DeleteMeterRequest(switchId, meterId), System.currentTimeMillis(), requestId);

        return messagingChannel.sendAndGet(floodlightTopic, deleteCommand)
                .thenApply(DeleteMeterResponse.class::cast)
                .thenApply(response -> new DeleteMeterResult(response.isDeleted()));
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<PortDto> configurePort(SwitchId switchId,  int port, PortConfigurationPayload config) {
        String correlationId = RequestCorrelationId.getId();

        PortConfigurationRequest request = new PortConfigurationRequest(switchId, 
                port, toPortAdminDown(config.getStatus()));
        CommandMessage updateStatusCommand = new CommandMessage(
                request, System.currentTimeMillis(), correlationId,
                Destination.CONTROLLER);

        return messagingChannel.sendAndGet(floodlightTopic, updateStatusCommand)
                .thenApply(PortConfigurationResponse.class::cast)
                .thenApply(response -> new PortDto(response.getSwitchId().toString(), response.getPortNo()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<SwitchPortsDescription> getSwitchPortsDescription(SwitchId switchId) {
        String correlationId = RequestCorrelationId.getId();
        DumpSwitchPortsDescriptionRequest request = new DumpSwitchPortsDescriptionRequest(switchId);
        CommandMessage commandMessage = new CommandMessage(request, System.currentTimeMillis(),
                correlationId, Destination.CONTROLLER);

        return messagingChannel.sendAndGet(floodlightTopic, commandMessage)
                .thenApply(SwitchPortsDescription.class::cast);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<PortDescription> getPortDescription(SwitchId switchId, int port) {
        String correlationId = RequestCorrelationId.getId();
        DumpPortDescriptionRequest request = new DumpPortDescriptionRequest(switchId, port);
        CommandMessage commandMessage = new CommandMessage(request, System.currentTimeMillis(),
                correlationId, Destination.CONTROLLER);

        return messagingChannel.sendAndGet(floodlightTopic, commandMessage)
                .thenApply(PortDescription.class::cast);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<SwitchDto> updateSwitchUnderMaintenance(SwitchId switchId,
            UnderMaintenanceDto underMaintenanceDto) {

        String correlationId = RequestCorrelationId.getId();
        logger.debug("Update under maintenance flag for switch request processing");
        UpdateSwitchUnderMaintenanceRequest data =
                new UpdateSwitchUnderMaintenanceRequest(switchId, underMaintenanceDto.isUnderMaintenance(),
                        underMaintenanceDto.isEvacuate());

        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId);

        return messagingChannel.sendAndGetChunked(nbworkerTopic, request)
                .thenApply(messages -> messages.stream()
                        .map(SwitchInfoData.class::cast)
                        .map(switchMapper::toSwitchDto)
                        .collect(Collectors.toList()).get(0));
    }

    private SwitchFlowEntries findByCookie(Long cookie, SwitchFlowEntries entries) {
        List<FlowEntry> matchedFlows = entries.getFlowEntries().stream()
                .filter(entry -> cookie.equals(entry.getCookie()))
                .collect(Collectors.toList());
        return new SwitchFlowEntries(entries.getSwitchId(), matchedFlows);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<DeleteSwitchResult> deleteSwitch(SwitchId switchId, boolean force) {
        String correlationId = RequestCorrelationId.getId();
        CommandMessage deleteCommand = new CommandMessage(
                new DeleteSwitchRequest(switchId, force), System.currentTimeMillis(), correlationId, Destination.WFM);
        return messagingChannel.sendAndGet(nbworkerTopic, deleteCommand)
                .thenApply(DeleteSwitchResponse.class::cast)
                .thenApply(response -> new DeleteSwitchResult(response.isDeleted()));
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
