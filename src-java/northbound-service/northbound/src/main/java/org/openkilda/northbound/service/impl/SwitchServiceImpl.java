/* Copyright 2019 Telstra Open Source
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

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
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
import org.openkilda.messaging.command.switches.SwitchValidateRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.MessageException;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowResponse;
import org.openkilda.messaging.info.meter.SwitchMeterEntries;
import org.openkilda.messaging.info.meter.SwitchMeterUnsupported;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.messaging.info.switches.ConnectModeResponse;
import org.openkilda.messaging.info.switches.DeleteMeterResponse;
import org.openkilda.messaging.info.switches.PortConfigurationResponse;
import org.openkilda.messaging.info.switches.PortDescription;
import org.openkilda.messaging.info.switches.SwitchPortsDescription;
import org.openkilda.messaging.info.switches.SwitchRulesResponse;
import org.openkilda.messaging.info.switches.SwitchSyncResponse;
import org.openkilda.messaging.info.switches.SwitchValidationResponse;
import org.openkilda.messaging.nbtopology.request.DeleteSwitchRequest;
import org.openkilda.messaging.nbtopology.request.GetFlowsForSwitchRequest;
import org.openkilda.messaging.nbtopology.request.GetPortPropertiesRequest;
import org.openkilda.messaging.nbtopology.request.GetSwitchConnectedDevicesRequest;
import org.openkilda.messaging.nbtopology.request.GetSwitchPropertiesRequest;
import org.openkilda.messaging.nbtopology.request.GetSwitchRequest;
import org.openkilda.messaging.nbtopology.request.GetSwitchesRequest;
import org.openkilda.messaging.nbtopology.request.PortHistoryRequest;
import org.openkilda.messaging.nbtopology.request.SwitchConnectionsRequest;
import org.openkilda.messaging.nbtopology.request.SwitchPatchRequest;
import org.openkilda.messaging.nbtopology.request.UpdatePortPropertiesRequest;
import org.openkilda.messaging.nbtopology.request.UpdateSwitchPropertiesRequest;
import org.openkilda.messaging.nbtopology.request.UpdateSwitchUnderMaintenanceRequest;
import org.openkilda.messaging.nbtopology.response.DeleteSwitchResponse;
import org.openkilda.messaging.nbtopology.response.GetSwitchResponse;
import org.openkilda.messaging.nbtopology.response.SwitchPropertiesResponse;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.history.PortHistoryPayload;
import org.openkilda.messaging.payload.switches.PortConfigurationPayload;
import org.openkilda.messaging.payload.switches.PortPropertiesPayload;
import org.openkilda.model.MacAddress;
import org.openkilda.model.PortStatus;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.converter.ConnectedDeviceMapper;
import org.openkilda.northbound.converter.FlowMapper;
import org.openkilda.northbound.converter.PortPropertiesMapper;
import org.openkilda.northbound.converter.SwitchMapper;
import org.openkilda.northbound.dto.v1.switches.DeleteMeterResult;
import org.openkilda.northbound.dto.v1.switches.DeleteSwitchResult;
import org.openkilda.northbound.dto.v1.switches.PortDto;
import org.openkilda.northbound.dto.v1.switches.RulesSyncResult;
import org.openkilda.northbound.dto.v1.switches.RulesValidationResult;
import org.openkilda.northbound.dto.v1.switches.SwitchDto;
import org.openkilda.northbound.dto.v1.switches.SwitchPropertiesDto;
import org.openkilda.northbound.dto.v1.switches.SwitchSyncResult;
import org.openkilda.northbound.dto.v1.switches.SwitchValidationResult;
import org.openkilda.northbound.dto.v1.switches.UnderMaintenanceDto;
import org.openkilda.northbound.dto.v2.switches.PortHistoryResponse;
import org.openkilda.northbound.dto.v2.switches.PortPropertiesDto;
import org.openkilda.northbound.dto.v2.switches.PortPropertiesResponse;
import org.openkilda.northbound.dto.v2.switches.SwitchConnectedDevicesResponse;
import org.openkilda.northbound.dto.v2.switches.SwitchConnectionsResponse;
import org.openkilda.northbound.dto.v2.switches.SwitchDtoV2;
import org.openkilda.northbound.dto.v2.switches.SwitchPatchDto;
import org.openkilda.northbound.messaging.MessagingChannel;
import org.openkilda.northbound.service.SwitchService;
import org.openkilda.northbound.utils.RequestCorrelationId;

import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class SwitchServiceImpl extends BaseService implements SwitchService {
    private static final Logger logger = LoggerFactory.getLogger(SwitchServiceImpl.class);

    @Autowired
    private MessagingChannel messagingChannel;

    @Autowired
    private SwitchMapper switchMapper;

    @Autowired
    private ConnectedDeviceMapper connectedDeviceMapper;

    @Autowired
    private PortPropertiesMapper portPropertiesMapper;

    @Autowired
    private FlowMapper flowMapper;

    @Value("#{kafkaTopicsConfig.getSpeakerTopic()}")
    private String floodlightTopic;

    @Value("#{kafkaTopicsConfig.getTopoNbTopic()}")
    private String nbworkerTopic;

    @Value("#{kafkaTopicsConfig.getTopoDiscoTopic()}")
    private String topoDiscoTopic;

    @Value("#{kafkaTopicsConfig.getTopoSwitchManagerNbTopic()}")
    private String switchManagerTopic;

    @Autowired
    public SwitchServiceImpl(MessagingChannel messagingChannel) {
        super(messagingChannel);
        this.messagingChannel = messagingChannel;
    }

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
                        .map(GetSwitchResponse.class::cast)
                        .map(GetSwitchResponse::getPayload)
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
                        .map(GetSwitchResponse.class::cast)
                        .map(GetSwitchResponse::getPayload)
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

        return messagingChannel.sendAndGet(switchManagerTopic, request)
                .thenApply(SwitchRulesResponse.class::cast)
                .thenApply(SwitchRulesResponse::getRuleIds);
    }

    @Override
    public CompletableFuture<List<Long>> deleteRules(SwitchId switchId, DeleteRulesCriteria criteria) {
        final String correlationId = RequestCorrelationId.getId();
        logger.info("Delete switch rules request received: switch={}, criteria={}", switchId, criteria);

        SwitchRulesDeleteRequest data = new SwitchRulesDeleteRequest(switchId, null, criteria);
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId);

        return messagingChannel.sendAndGet(switchManagerTopic, request)
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

        return messagingChannel.sendAndGet(switchManagerTopic, request)
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
        logger.info("Validate rules request for switch {}", switchId);

        return performValidate(
                SwitchValidateRequest.builder().switchId(switchId).build())
                .thenApply(switchMapper::toRulesValidationResult);
    }

    @Override
    public CompletableFuture<SwitchValidationResult> validateSwitch(SwitchId switchId) {
        logger.info("Validate request for switch {}", switchId);

        return performValidate(
                SwitchValidateRequest.builder().switchId(switchId).processMeters(true).build())
                .thenApply(switchMapper::toSwitchValidationResult);
    }

    private CompletableFuture<SwitchValidationResponse> performValidate(SwitchValidateRequest request) {
        CommandMessage validateCommandMessage = new CommandMessage(
                request,
                System.currentTimeMillis(), RequestCorrelationId.getId());

        return messagingChannel.sendAndGet(switchManagerTopic, validateCommandMessage)
                .thenApply(SwitchValidationResponse.class::cast);
    }

    @Override
    public CompletableFuture<RulesSyncResult> syncRules(SwitchId switchId) {
        logger.info("Sync rules request for switch {}", switchId);

        return performSync(
                SwitchValidateRequest.builder().switchId(switchId).performSync(true).build())
                .thenApply(switchMapper::toRulesSyncResult);
    }

    @Override
    public CompletableFuture<SwitchSyncResult> syncSwitch(SwitchId switchId, boolean removeExcess) {
        logger.info("Sync request for switch {}. Remove excess {}", switchId, removeExcess);

        return performSync(
                SwitchValidateRequest.builder().switchId(switchId).processMeters(true).performSync(true)
                        .removeExcess(removeExcess).build())
                .thenApply(switchMapper::toSwitchSyncResult);
    }

    private CompletableFuture<SwitchSyncResponse> performSync(SwitchValidateRequest request) {
        CommandMessage validateCommandMessage = new CommandMessage(
                request,
                System.currentTimeMillis(), RequestCorrelationId.getId());

        return messagingChannel.sendAndGet(switchManagerTopic, validateCommandMessage)
                .thenApply(SwitchSyncResponse.class::cast);
    }

    @Override
    public CompletableFuture<SwitchMeterEntries> getMeters(SwitchId switchId) {
        String requestId = RequestCorrelationId.getId();
        CommandMessage dumpCommand = new CommandMessage(
                new DumpMetersRequest(switchId), System.currentTimeMillis(), requestId);

        return messagingChannel.sendAndGet(floodlightTopic, dumpCommand)
                .thenApply(infoData -> {
                    if (infoData instanceof SwitchMeterEntries) {
                        return (SwitchMeterEntries) infoData;
                    } else if (infoData instanceof SwitchMeterUnsupported) {
                        return SwitchMeterEntries.builder().switchId(switchId).build();
                    } else {
                        throw new IllegalArgumentException("Unhandled meters response for switch " + switchId);
                    }
                });
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

    @Override
    public CompletableFuture<List<PortHistoryResponse>> getPortHistory(SwitchId switchId, int port,
                                                                       Instant from, Instant to) {
        PortHistoryRequest request = new PortHistoryRequest(switchId, port, from, to);
        Message message = new CommandMessage(request, System.currentTimeMillis(), RequestCorrelationId.getId());
        return messagingChannel.sendAndGetChunked(nbworkerTopic, message)
                .thenApply(responses -> responses.stream()
                        .map(PortHistoryPayload.class::cast)
                        .map(switchMapper::map)
                        .collect(Collectors.toList()));
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
                        .map(GetSwitchResponse.class::cast)
                        .map(GetSwitchResponse::getPayload)
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

    @Override
    public CompletableFuture<List<FlowPayload>> getFlowsForSwitch(SwitchId switchId, Integer port) {
        final String correlationId = RequestCorrelationId.getId();
        logger.debug("Get all flows for the switch: {}", switchId);
        GetFlowsForSwitchRequest data = new GetFlowsForSwitchRequest(switchId, port);
        CommandMessage message = new CommandMessage(data, System.currentTimeMillis(), correlationId);

        return messagingChannel.sendAndGetChunked(nbworkerTopic, message)
                .thenApply(response -> response.stream()
                        .map(FlowResponse.class::cast)
                        .map(FlowResponse::getPayload)
                        .map(flowMapper::toFlowOutput)
                        .collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<SwitchPropertiesDto> getSwitchProperties(SwitchId switchId) {
        final String correlationId = RequestCorrelationId.getId();
        logger.debug("Get switch properties for the switch: {}", switchId);
        GetSwitchPropertiesRequest data = new GetSwitchPropertiesRequest(switchId);
        CommandMessage message = new CommandMessage(data, System.currentTimeMillis(), correlationId);

        return messagingChannel.sendAndGet(nbworkerTopic, message)
                .thenApply(SwitchPropertiesResponse.class::cast)
                .thenApply(response -> switchMapper.map(response.getSwitchProperties()));
    }

    @Override
    public CompletableFuture<SwitchPropertiesDto> updateSwitchProperties(SwitchId switchId,
                                                                         SwitchPropertiesDto switchPropertiesDto) {

        String correlationId = RequestCorrelationId.getId();
        logger.info("Update switch properties for the switch: {}, New properties: {}", switchId, switchPropertiesDto);

        if (switchPropertiesDto.getServer42Port() != null && switchPropertiesDto.getServer42Port() <= 0) {
            throw new MessageException(ErrorType.REQUEST_INVALID, format(
                    "Property 'server42_port' for switch %s has invalid value '%d'. Port must be positive",
                    switchId, switchPropertiesDto.getServer42Port()), "Invalid server 42 Port");
        }

        if (switchPropertiesDto.getServer42Vlan() != null
                && (switchPropertiesDto.getServer42Vlan() < 0 || switchPropertiesDto.getServer42Vlan() > 4095)) {
            throw new MessageException(ErrorType.REQUEST_INVALID, format(
                    "Property 'server42_vlan' for switch %s has invalid value '%d'. Vlan must be in range [0, 4095]",
                    switchId, switchPropertiesDto.getServer42Vlan()), "Invalid server 42 Vlan");
        }

        if (switchPropertiesDto.getServer42MacAddress() != null
                && !MacAddress.isValid(switchPropertiesDto.getServer42MacAddress())) {
            throw new MessageException(ErrorType.REQUEST_INVALID, format(
                    "Property 'server42_mac_address' for switch %s has invalid value '%s'.",
                    switchId, switchPropertiesDto.getServer42MacAddress()), "Invalid server 42 Mac Address");
        }

        UpdateSwitchPropertiesRequest data = null;
        try {
            data = new UpdateSwitchPropertiesRequest(switchId, switchMapper.map(switchPropertiesDto));
            CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId);
            return messagingChannel.sendAndGet(nbworkerTopic, request)
                    .thenApply(SwitchPropertiesResponse.class::cast)
                    .thenApply(response -> switchMapper.map(response.getSwitchProperties()));
        } catch (IllegalArgumentException e) {
            throw new MessageException(ErrorType.REQUEST_INVALID, "Unable to parse request payload", e.getMessage());
        }
    }

    @Override
    public CompletableFuture<PortPropertiesResponse> getPortProperties(SwitchId switchId, int port) {
        String correlationId = RequestCorrelationId.getId();
        logger.debug("Get port properties for the switch {} and port {}", switchId, port);
        GetPortPropertiesRequest data = new GetPortPropertiesRequest(switchId, port);
        CommandMessage message = new CommandMessage(data, System.currentTimeMillis(), correlationId);

        return messagingChannel.sendAndGet(nbworkerTopic, message)
                .thenApply(PortPropertiesPayload.class::cast)
                .thenApply(portPropertiesMapper::map);
    }

    @Override
    public CompletableFuture<PortPropertiesResponse> updatePortProperties(SwitchId switchId, int port,
                                                                          PortPropertiesDto portPropertiesDto) {
        String correlationId = RequestCorrelationId.getId();
        logger.info("Update port properties for the switch {} and port {}. New properties {}",
                switchId, port, portPropertiesDto);
        UpdatePortPropertiesRequest data = new UpdatePortPropertiesRequest(switchId, port,
                portPropertiesDto.isDiscoveryEnabled());
        InfoMessage request = new InfoMessage(data, System.currentTimeMillis(), correlationId);
        return messagingChannel.sendAndGet(topoDiscoTopic, request)
                .thenApply(PortPropertiesPayload.class::cast)
                .thenApply(portPropertiesMapper::map);
    }

    @Override
    public CompletableFuture<SwitchConnectedDevicesResponse> getSwitchConnectedDevices(
            SwitchId switchId, Instant since) {
        logger.info("Get connected devices for switch {} since {}", switchId, since);

        GetSwitchConnectedDevicesRequest request = new GetSwitchConnectedDevicesRequest(switchId, since);

        CommandMessage message = new CommandMessage(
                request, System.currentTimeMillis(), RequestCorrelationId.getId(), Destination.WFM);

        return messagingChannel.sendAndGet(nbworkerTopic, message)
                .thenApply(org.openkilda.messaging.nbtopology.response.SwitchConnectedDevicesResponse.class::cast)
                .thenApply(connectedDeviceMapper::map);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<SwitchDtoV2> patchSwitch(SwitchId switchId, SwitchPatchDto dto) {
        logger.info("Patch switch request for switch {}", switchId);

        CommandMessage request = new CommandMessage(new SwitchPatchRequest(switchId, switchMapper.map(dto)),
                System.currentTimeMillis(), RequestCorrelationId.getId());

        return messagingChannel.sendAndGet(nbworkerTopic, request)
                .thenApply(GetSwitchResponse.class::cast)
                .thenApply(GetSwitchResponse::getPayload)
                .thenApply(switchMapper::map);
    }

    @Override
    public CompletableFuture<SwitchConnectionsResponse> getSwitchConnections(SwitchId switchId) {
        logger.info("Get connections between switch {} and OF speakers", switchId);

        return sendRequest(nbworkerTopic, new SwitchConnectionsRequest(switchId))
                .thenApply(org.openkilda.messaging.nbtopology.response.SwitchConnectionsResponse.class::cast)
                .thenApply(switchMapper::map);
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
                throw new IllegalArgumentException(format(
                        "Unsupported enum %s value: %s", PortStatus.class.getName(), status));
        }
        return adminDownState;
    }
}
