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

import static com.google.common.collect.Sets.newHashSet;
import static java.lang.String.format;
import static org.openkilda.messaging.model.ValidationFilter.FLOW_INFO;
import static org.openkilda.messaging.model.ValidationFilter.GROUPS;
import static org.openkilda.messaging.model.ValidationFilter.LOGICAL_PORTS;
import static org.openkilda.messaging.model.ValidationFilter.METERS;
import static org.openkilda.messaging.model.ValidationFilter.RULES;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.DeleteMeterRequest;
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
import org.openkilda.messaging.info.switches.DeleteMeterResponse;
import org.openkilda.messaging.info.switches.PortConfigurationResponse;
import org.openkilda.messaging.info.switches.PortDescription;
import org.openkilda.messaging.info.switches.SwitchPortsDescription;
import org.openkilda.messaging.info.switches.SwitchRulesResponse;
import org.openkilda.messaging.info.switches.SwitchSyncResponse;
import org.openkilda.messaging.info.switches.v2.SwitchValidationResponseV2;
import org.openkilda.messaging.model.ValidationFilter;
import org.openkilda.messaging.nbtopology.request.DeleteSwitchRequest;
import org.openkilda.messaging.nbtopology.request.GetAllSwitchPropertiesRequest;
import org.openkilda.messaging.nbtopology.request.GetFlowsForSwitchRequest;
import org.openkilda.messaging.nbtopology.request.GetFlowsPerPortForSwitchRequest;
import org.openkilda.messaging.nbtopology.request.GetLacpStatusRequest;
import org.openkilda.messaging.nbtopology.request.GetPortPropertiesRequest;
import org.openkilda.messaging.nbtopology.request.GetSwitchConnectedDevicesRequest;
import org.openkilda.messaging.nbtopology.request.GetSwitchLacpStatusRequest;
import org.openkilda.messaging.nbtopology.request.GetSwitchLagPortsRequest;
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
import org.openkilda.messaging.nbtopology.response.GetFlowsPerPortForSwitchResponse;
import org.openkilda.messaging.nbtopology.response.GetSwitchResponse;
import org.openkilda.messaging.nbtopology.response.SwitchLacpStatusResponse;
import org.openkilda.messaging.nbtopology.response.SwitchLagPortResponse;
import org.openkilda.messaging.nbtopology.response.SwitchPropertiesResponse;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.history.PortHistoryPayload;
import org.openkilda.messaging.payload.switches.PortConfigurationPayload;
import org.openkilda.messaging.payload.switches.PortPropertiesPayload;
import org.openkilda.messaging.swmanager.request.CreateLagPortRequest;
import org.openkilda.messaging.swmanager.request.DeleteLagPortRequest;
import org.openkilda.messaging.swmanager.request.UpdateLagPortRequest;
import org.openkilda.model.MacAddress;
import org.openkilda.model.PortStatus;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.converter.ConnectedDeviceMapper;
import org.openkilda.northbound.converter.FlowMapper;
import org.openkilda.northbound.converter.LacpStatusMapper;
import org.openkilda.northbound.converter.LagPortMapper;
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
import org.openkilda.northbound.dto.v2.switches.LacpStatusResponse;
import org.openkilda.northbound.dto.v2.switches.LagPortRequest;
import org.openkilda.northbound.dto.v2.switches.LagPortResponse;
import org.openkilda.northbound.dto.v2.switches.PortHistoryResponse;
import org.openkilda.northbound.dto.v2.switches.PortPropertiesDto;
import org.openkilda.northbound.dto.v2.switches.PortPropertiesResponse;
import org.openkilda.northbound.dto.v2.switches.SwitchConnectedDevicesResponse;
import org.openkilda.northbound.dto.v2.switches.SwitchConnectionsResponse;
import org.openkilda.northbound.dto.v2.switches.SwitchDtoV2;
import org.openkilda.northbound.dto.v2.switches.SwitchFlowsPerPortResponse;
import org.openkilda.northbound.dto.v2.switches.SwitchPatchDto;
import org.openkilda.northbound.dto.v2.switches.SwitchPropertiesDump;
import org.openkilda.northbound.dto.v2.switches.SwitchValidationResultV2;
import org.openkilda.northbound.messaging.MessagingChannel;
import org.openkilda.northbound.service.SwitchService;
import org.openkilda.northbound.utils.RequestCorrelationId;

import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SwitchServiceImpl extends BaseService implements SwitchService {
    public static final String SWITCH_VALIDATION_FILTERS_SPLITTER = "\\|";
    public static final Set<ValidationFilter> VALID_INCLUDE_FILTERS = Sets.newHashSet(
            RULES, METERS, GROUPS, LOGICAL_PORTS);
    public static final Set<ValidationFilter> VALID_EXCLUDE_FILTERS = Sets.newHashSet(FLOW_INFO);


    @Autowired
    private MessagingChannel messagingChannel;

    @Autowired
    private SwitchMapper switchMapper;

    @Autowired
    private LagPortMapper lagPortMapper;

    @Autowired
    private LacpStatusMapper lacpStatusMapper;

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
        log.info("API request: Get switch request received");
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
        log.info("API request: Get one switch request {}", switchId);
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
        log.info("API request: Get rules: switch={}, cookie={}", switchId, cookie);
        return getRules(switchId, cookie, RequestCorrelationId.getId());
    }

    @Override
    public CompletableFuture<List<Long>> deleteRules(SwitchId switchId, DeleteRulesAction deleteAction) {
        final String correlationId = RequestCorrelationId.getId();
        log.info("API request: Delete switch rules request received: switch={}, deleteAction={}",
                switchId, deleteAction);

        SwitchRulesDeleteRequest data = new SwitchRulesDeleteRequest(switchId, deleteAction, null);
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId);

        return messagingChannel.sendAndGet(switchManagerTopic, request)
                .thenApply(SwitchRulesResponse.class::cast)
                .thenApply(SwitchRulesResponse::getRuleIds);
    }

    @Override
    public CompletableFuture<List<Long>> deleteRules(SwitchId switchId, DeleteRulesCriteria criteria) {
        final String correlationId = RequestCorrelationId.getId();
        log.info("API request: Delete switch rules request received: switch={}, criteria={}", switchId, criteria);

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
        log.info("API request: Install switch rules request received: switch={}, action={}", switchId, installAction);

        SwitchRulesInstallRequest data = new SwitchRulesInstallRequest(switchId, installAction);
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId);

        return messagingChannel.sendAndGet(switchManagerTopic, request)
                .thenApply(SwitchRulesResponse.class::cast)
                .thenApply(SwitchRulesResponse::getRuleIds);
    }

    @Override
    public CompletableFuture<RulesValidationResult> validateRules(SwitchId switchId) {
        log.info("API request: Validate rules request for switch {}", switchId);

        return performValidateV2(
                SwitchValidateRequest.builder()
                        .validationFilters(newHashSet(RULES))
                        .switchId(switchId).build())
                .thenApply(switchMapper::toRulesValidationResult);
    }

    @Override
    public CompletableFuture<SwitchValidationResult> validateSwitch(SwitchId switchId) {
        log.info("API request: Validate request for switch {}", switchId);

        return performValidateV2(
                SwitchValidateRequest.builder()
                        .switchId(switchId)
                        .validationFilters(ValidationFilter.ALL_WITH_METER_FLOW_INFO)
                        .build())
                .thenApply(switchMapper::toSwitchValidationResultV1);
    }

    @Override
    public CompletableFuture<SwitchValidationResultV2> validateSwitch(
            SwitchId switchId, String includeString, String excludeString) {
        log.info("API request: Validate api V2 request for switch {}. includeString={}, excludeString={}",
                switchId, includeString, excludeString);

        Set<ValidationFilter> includeFilters = parseSwitchValidationIncludeFilters(includeString);
        if (includeFilters.isEmpty()) {
            includeFilters.addAll(VALID_INCLUDE_FILTERS);
        }

        Set<ValidationFilter> excludeFilters = parseSwitchValidationExcludeFilters(excludeString);

        Set<ValidationFilter> filters = new HashSet<>(includeFilters);
        filters.add(FLOW_INFO);
        filters.removeAll(excludeFilters);

        return performValidateV2(
                SwitchValidateRequest.builder().switchId(switchId)
                        .validationFilters(filters)
                        .build())
                .thenApply(switchMapper::toSwitchValidationResultV2);
    }

    private CompletableFuture<SwitchValidationResponseV2> performValidateV2(SwitchValidateRequest request) {
        CommandMessage validateCommandMessage = new CommandMessage(
                request,
                System.currentTimeMillis(), RequestCorrelationId.getId());

        return messagingChannel.sendAndGetChunked(switchManagerTopic, validateCommandMessage)
                .thenApply(response -> SwitchValidationResponseV2.unite(
                        response.stream()
                                .map(SwitchValidationResponseV2.class::cast)
                                .collect(Collectors.toList())));
    }

    @Override
    public CompletableFuture<RulesSyncResult> syncRules(SwitchId switchId) {
        log.info("API request: Sync rules request for switch {}", switchId);

        return performSync(
                SwitchValidateRequest.builder()
                        .switchId(switchId)
                        .validationFilters(Sets.newHashSet(RULES))
                        .performSync(true)
                        .build())
                .thenApply(switchMapper::toRulesSyncResult);
    }

    @Override
    public CompletableFuture<SwitchSyncResult> syncSwitch(SwitchId switchId, boolean removeExcess) {
        log.info("API request: Sync request for switch {}. Remove excess {}", switchId, removeExcess);

        return performSync(
                SwitchValidateRequest.builder().switchId(switchId)
                        .performSync(true)
                        .removeExcess(removeExcess)
                        .validationFilters(ValidationFilter.ALL_WITH_METER_FLOW_INFO)
                        .build())
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
        log.info("API request: Get meters: switch={}", switchId);
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
        log.info("API request: Delete meter: switch={}, meterId={}", switchId, meterId);

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
    public CompletableFuture<PortDto> configurePort(SwitchId switchId, int port, PortConfigurationPayload config) {
        log.info("API request: Configure port {}_{}, config={}", switchId, port, config);
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
        log.info("API request: Get switch description {}", switchId);
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
        log.info("API request: Get port description {}_{}", switchId, port);
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
        log.info("API request: Get port history {}_{} from {} to {} ", switchId, port, from, to);
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
        log.info("API request: Update switch under maintenance. switch={}, flag={}", switchId, underMaintenanceDto);

        String correlationId = RequestCorrelationId.getId();
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
        log.info("API request: Delete switch {}, force={}", switchId, force);

        String correlationId = RequestCorrelationId.getId();
        CommandMessage deleteCommand = new CommandMessage(
                new DeleteSwitchRequest(switchId, force), System.currentTimeMillis(), correlationId, Destination.WFM);
        return messagingChannel.sendAndGet(nbworkerTopic, deleteCommand)
                .thenApply(DeleteSwitchResponse.class::cast)
                .thenApply(response -> new DeleteSwitchResult(response.isDeleted()));
    }

    @Override
    public CompletableFuture<List<FlowPayload>> getFlowsForSwitch(SwitchId switchId, Integer port) {
        log.info("API request: Get all flows for the switch: {}_{}", switchId, port);
        final String correlationId = RequestCorrelationId.getId();
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
    public CompletableFuture<SwitchFlowsPerPortResponse> getFlowsPerPortForSwitch(SwitchId switchId,
                                                                                  Collection<Integer> ports) {
        log.info("API request: Get all flows per port for the switch: {} with the port filter: {}", switchId, ports);
        final String correlationId = RequestCorrelationId.getId();
        GetFlowsPerPortForSwitchRequest data = new GetFlowsPerPortForSwitchRequest(switchId, ports);
        CommandMessage message = new CommandMessage(data, System.currentTimeMillis(), correlationId);

        return messagingChannel.sendAndGet(nbworkerTopic, message)
                .thenApply(GetFlowsPerPortForSwitchResponse.class::cast)
                .thenApply(switchMapper::toSwitchFlowsPerPortResponseV2Api);
    }


    @Override
    public CompletableFuture<SwitchPropertiesDto> getSwitchProperties(SwitchId switchId) {
        log.info("API request: Get switch properties for the switch: {}", switchId);
        final String correlationId = RequestCorrelationId.getId();
        GetSwitchPropertiesRequest data = new GetSwitchPropertiesRequest(switchId);
        CommandMessage message = new CommandMessage(data, System.currentTimeMillis(), correlationId);

        return messagingChannel.sendAndGet(nbworkerTopic, message)
                .thenApply(SwitchPropertiesResponse.class::cast)
                .thenApply(response -> switchMapper.map(response.getSwitchProperties()));
    }

    @Override
    public CompletableFuture<SwitchPropertiesDump> dumpSwitchProperties() {
        log.info("API request: Dump switch properties");
        final String correlationId = RequestCorrelationId.getId();
        CommandMessage request = new CommandMessage(new GetAllSwitchPropertiesRequest(), System.currentTimeMillis(),
                correlationId);

        return messagingChannel.sendAndGetChunked(nbworkerTopic, request)
                .thenApply(messages -> messages.stream()
                        .map(SwitchPropertiesResponse.class::cast)
                        .map(r -> switchMapper.map(r.getSwitchProperties()))
                        .collect(Collectors.toList()))
                .thenApply(dump -> new SwitchPropertiesDump(dump));
    }

    @Override
    public CompletableFuture<SwitchPropertiesDto> updateSwitchProperties(SwitchId switchId,
                                                                         SwitchPropertiesDto switchPropertiesDto) {

        String correlationId = RequestCorrelationId.getId();
        log.info("API request: Update switch properties for the switch: {}, New properties: {}",
                switchId, switchPropertiesDto);

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
        log.info("API request: Get port properties for the switch {} and port {}", switchId, port);
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
        log.info("API request: Update port properties for the switch {} and port {}. New properties {}",
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
        log.info("API request: Get connected devices for switch {} since {}", switchId, since);

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
        log.info("API request: Patch switch request for switch {}. New properties {}", switchId, dto);

        CommandMessage request = new CommandMessage(new SwitchPatchRequest(switchId, switchMapper.map(dto)),
                System.currentTimeMillis(), RequestCorrelationId.getId());

        return messagingChannel.sendAndGet(nbworkerTopic, request)
                .thenApply(GetSwitchResponse.class::cast)
                .thenApply(GetSwitchResponse::getPayload)
                .thenApply(switchMapper::map);
    }

    @Override
    public CompletableFuture<SwitchConnectionsResponse> getSwitchConnections(SwitchId switchId) {
        log.info("API request: Get connections between switch {} and OF speakers", switchId);

        return sendRequest(nbworkerTopic, new SwitchConnectionsRequest(switchId))
                .thenApply(org.openkilda.messaging.nbtopology.response.SwitchConnectionsResponse.class::cast)
                .thenApply(switchMapper::map);
    }

    @Override
    public CompletableFuture<LagPortResponse> createLag(SwitchId switchId, LagPortRequest lagPortDto) {
        log.info("API request: Create Link aggregation group on switch {}, ports {}, LACP reply {}",
                switchId, lagPortDto.getPortNumbers(), lagPortDto.getLacpReply());

        CreateLagPortRequest data = new CreateLagPortRequest(switchId, lagPortDto.getPortNumbers(),
                lagPortDto.getLacpReply());
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), RequestCorrelationId.getId());

        return messagingChannel.sendAndGet(switchManagerTopic, request)
                .thenApply(org.openkilda.messaging.swmanager.response.LagPortResponse.class::cast)
                .thenApply(lagPortMapper::map);
    }

    @Override
    public CompletableFuture<List<LagPortResponse>> getLagPorts(SwitchId switchId) {
        log.info("API request: Getting Link aggregation groups on switch {}", switchId);

        GetSwitchLagPortsRequest data = new GetSwitchLagPortsRequest(switchId);
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), RequestCorrelationId.getId());

        return messagingChannel.sendAndGetChunked(nbworkerTopic, request)
                .thenApply(result -> result.stream()
                        .map(SwitchLagPortResponse.class::cast)
                        .map(SwitchLagPortResponse::getData)
                        .map(lagPortMapper::map)
                        .collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<List<LacpStatusResponse>> getLacpStatus(SwitchId switchId) {
        log.info("API request: Getting LACP status on switch {}", switchId);

        GetSwitchLacpStatusRequest data = new GetSwitchLacpStatusRequest(switchId);
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), RequestCorrelationId.getId());

        return messagingChannel.sendAndGetChunked(nbworkerTopic, request)
                .thenApply(result -> result.stream()
                        .map(SwitchLacpStatusResponse.class::cast)
                        .map(SwitchLacpStatusResponse::getData)
                        .map(lacpStatusMapper::map)
                        .collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<LacpStatusResponse> getLacpStatus(SwitchId switchId, int logicalPortNumber) {
        log.info("API request: Read LACP status on specific port {} of the switch {}", logicalPortNumber, switchId);

        GetLacpStatusRequest data = new GetLacpStatusRequest(switchId, logicalPortNumber);
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), RequestCorrelationId.getId());

        return messagingChannel.sendAndGet(nbworkerTopic, request)
                .thenApply(SwitchLacpStatusResponse.class::cast)
                .thenApply(SwitchLacpStatusResponse::getData)
                .thenApply(lacpStatusMapper::map);
    }

    @Override
    public CompletableFuture<LagPortResponse> updateLagPort(
            SwitchId switchId, int logicalPortNumber, LagPortRequest payload) {
        log.info("API request: Updating LAG logical port {} on {} with {}", logicalPortNumber, switchId, payload);

        UpdateLagPortRequest request = new UpdateLagPortRequest(switchId, logicalPortNumber, payload.getPortNumbers(),
                payload.getLacpReply());
        CommandMessage message = new CommandMessage(request, System.currentTimeMillis(), RequestCorrelationId.getId());
        return messagingChannel.sendAndGet(switchManagerTopic, message)
                .thenApply(org.openkilda.messaging.swmanager.response.LagPortResponse.class::cast)
                .thenApply(lagPortMapper::map);
    }

    @Override
    public CompletableFuture<LagPortResponse> deleteLagPort(SwitchId switchId, int logicalPortNumber) {
        log.info("API request: Removing Link aggregation group {} on switch {}", logicalPortNumber, switchId);

        DeleteLagPortRequest data = new DeleteLagPortRequest(switchId, logicalPortNumber);
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), RequestCorrelationId.getId());

        return messagingChannel.sendAndGet(switchManagerTopic, request)
                .thenApply(org.openkilda.messaging.swmanager.response.LagPortResponse.class::cast)
                .thenApply(lagPortMapper::map);
    }

    private Boolean toPortAdminDown(PortStatus status) {
        if (status == null) {
            return null;
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


    private Set<ValidationFilter> parseSwitchValidationIncludeFilters(String query) {
        try {
            return parseAndValidateFilters(query, VALID_INCLUDE_FILTERS);
        } catch (IllegalArgumentException e) {
            throw new MessageException(ErrorType.REQUEST_INVALID, e.getMessage(), String.format(
                    "Value of include filter is invalid. Valid values are: %s", VALID_INCLUDE_FILTERS));
        }
    }

    private Set<ValidationFilter> parseSwitchValidationExcludeFilters(String query) {
        try {
            return parseAndValidateFilters(query, VALID_EXCLUDE_FILTERS);
        } catch (IllegalArgumentException e) {
            throw new MessageException(ErrorType.REQUEST_INVALID, e.getMessage(), String.format(
                    "Value of exclude filter is invalid. Valid values are: %s", VALID_EXCLUDE_FILTERS));
        }
    }

    private Set<ValidationFilter> parseAndValidateFilters(String query, Set<ValidationFilter> validFilters) {
        Set<ValidationFilter> filters = new HashSet<>();
        if (query == null) {
            return filters;
        }

        for (String stringFilter : query.split(SWITCH_VALIDATION_FILTERS_SPLITTER)) {
            ValidationFilter filter;
            try {
                filter = switchMapper.toValidationFilter(stringFilter);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(String.format("Filter has invalid value '%s'", stringFilter));
            }
            if (!validFilters.contains(filter)) {
                throw new IllegalArgumentException(String.format("Filter has invalid value '%s'", stringFilter));
            }
            filters.add(filter);
        }
        return filters;
    }
}


