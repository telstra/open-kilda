/* Copyright 2022 Telstra Open Source
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

package org.openkilda.wfm.topology.switchmanager.service;

import static java.lang.String.format;
import static org.openkilda.model.cookie.Cookie.ARP_INGRESS_COOKIE;
import static org.openkilda.model.cookie.Cookie.ARP_INPUT_PRE_DROP_COOKIE;
import static org.openkilda.model.cookie.Cookie.ARP_POST_INGRESS_COOKIE;
import static org.openkilda.model.cookie.Cookie.ARP_POST_INGRESS_ONE_SWITCH_COOKIE;
import static org.openkilda.model.cookie.Cookie.ARP_POST_INGRESS_VXLAN_COOKIE;
import static org.openkilda.model.cookie.Cookie.ARP_TRANSIT_COOKIE;
import static org.openkilda.model.cookie.Cookie.CATCH_BFD_RULE_COOKIE;
import static org.openkilda.model.cookie.Cookie.DROP_RULE_COOKIE;
import static org.openkilda.model.cookie.Cookie.DROP_VERIFICATION_LOOP_RULE_COOKIE;
import static org.openkilda.model.cookie.Cookie.LLDP_INGRESS_COOKIE;
import static org.openkilda.model.cookie.Cookie.LLDP_INPUT_PRE_DROP_COOKIE;
import static org.openkilda.model.cookie.Cookie.LLDP_POST_INGRESS_COOKIE;
import static org.openkilda.model.cookie.Cookie.LLDP_POST_INGRESS_ONE_SWITCH_COOKIE;
import static org.openkilda.model.cookie.Cookie.LLDP_POST_INGRESS_VXLAN_COOKIE;
import static org.openkilda.model.cookie.Cookie.LLDP_TRANSIT_COOKIE;
import static org.openkilda.model.cookie.Cookie.MULTITABLE_EGRESS_PASS_THROUGH_COOKIE;
import static org.openkilda.model.cookie.Cookie.MULTITABLE_INGRESS_DROP_COOKIE;
import static org.openkilda.model.cookie.Cookie.MULTITABLE_POST_INGRESS_DROP_COOKIE;
import static org.openkilda.model.cookie.Cookie.MULTITABLE_PRE_INGRESS_PASS_THROUGH_COOKIE;
import static org.openkilda.model.cookie.Cookie.MULTITABLE_TRANSIT_DROP_COOKIE;
import static org.openkilda.model.cookie.Cookie.ROUND_TRIP_LATENCY_RULE_COOKIE;
import static org.openkilda.model.cookie.Cookie.SERVER_42_FLOW_RTT_OUTPUT_VLAN_COOKIE;
import static org.openkilda.model.cookie.Cookie.SERVER_42_FLOW_RTT_OUTPUT_VXLAN_COOKIE;
import static org.openkilda.model.cookie.Cookie.SERVER_42_FLOW_RTT_TURNING_COOKIE;
import static org.openkilda.model.cookie.Cookie.SERVER_42_FLOW_RTT_VXLAN_TURNING_COOKIE;
import static org.openkilda.model.cookie.Cookie.SERVER_42_ISL_RTT_OUTPUT_COOKIE;
import static org.openkilda.model.cookie.Cookie.SERVER_42_ISL_RTT_TURNING_COOKIE;
import static org.openkilda.model.cookie.Cookie.VERIFICATION_BROADCAST_RULE_COOKIE;
import static org.openkilda.model.cookie.Cookie.VERIFICATION_UNICAST_RULE_COOKIE;
import static org.openkilda.model.cookie.Cookie.VERIFICATION_UNICAST_VXLAN_RULE_COOKIE;

import org.openkilda.floodlight.api.request.rulemanager.FlowCommand;
import org.openkilda.floodlight.api.request.rulemanager.GroupCommand;
import org.openkilda.floodlight.api.request.rulemanager.MeterCommand;
import org.openkilda.floodlight.api.request.rulemanager.OfCommand;
import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.messaging.MessageCookie;
import org.openkilda.messaging.command.switches.InstallRulesAction;
import org.openkilda.messaging.command.switches.SwitchRulesDeleteRequest;
import org.openkilda.messaging.command.switches.SwitchRulesInstallRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.switches.SwitchRulesResponse;
import org.openkilda.model.FlowPath;
import org.openkilda.model.KildaFeatureToggles;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.model.cookie.CookieBase;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.KildaFeatureTogglesRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.rulemanager.DataAdapter;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.MeterSpeakerData;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.adapter.PersistenceDataAdapter;
import org.openkilda.wfm.error.MessageDispatchException;
import org.openkilda.wfm.topology.switchmanager.bolt.SwitchManagerHub.OfCommandAction;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class SwitchRuleService implements SwitchManagerHubService {
    @Getter
    private SwitchManagerCarrier carrier;

    private FlowPathRepository flowPathRepository;
    private SwitchPropertiesRepository switchPropertiesRepository;
    private KildaFeatureTogglesRepository featureTogglesRepository;
    private IslRepository islRepository;
    private SwitchRepository switchRepository;
    private RuleManager ruleManager;
    private PersistenceManager persistenceManager;

    private boolean active = true;

    private boolean isOperationCompleted = true;

    private Map<String, List<OfCommand>> sentCommands = new HashMap<>();

    public SwitchRuleService(SwitchManagerCarrier carrier, PersistenceManager persistenceManager,
                             RuleManager ruleManager) {
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        flowPathRepository = repositoryFactory.createFlowPathRepository();
        switchPropertiesRepository = repositoryFactory.createSwitchPropertiesRepository();
        featureTogglesRepository = repositoryFactory.createFeatureTogglesRepository();
        islRepository = repositoryFactory.createIslRepository();
        switchRepository = repositoryFactory.createSwitchRepository();
        this.persistenceManager = persistenceManager;
        this.ruleManager = ruleManager;
        this.carrier = carrier;
    }

    @Override
    public void timeout(@NonNull MessageCookie cookie) {
        log.info("Got timeout notification for request key \"{}\"", cookie.getValue());
    }

    @Override
    public void dispatchWorkerMessage(InfoData payload, MessageCookie cookie) throws MessageDispatchException {
        if (payload instanceof SwitchRulesResponse) {
            handleRulesResponse(cookie.getValue(), (SwitchRulesResponse) payload);
        } else {
            throw new MessageDispatchException(cookie);
        }
    }

    @Override
    public void dispatchWorkerMessage(SpeakerResponse payload, MessageCookie cookie) throws MessageDispatchException {
        if (payload instanceof SpeakerCommandResponse) {
            handleRulesResponse(cookie.getValue(), (SpeakerCommandResponse) payload);
        } else {
            throw new MessageDispatchException(cookie);
        }
    }


    @Override
    public void dispatchErrorMessage(ErrorData payload, MessageCookie cookie) {
        // FIXME(surabujin): the service completely ignores error responses
        log.error("Got speaker error response: {} (request key: {})", payload, cookie.getValue());
    }

    /**
     * Handle delete rules request.
     */
    public void deleteRules(String key, SwitchRulesDeleteRequest data) {
        isOperationCompleted = false;  // FIXME(surabujin): what it supposed to do? Can we get rid of it?
        SwitchId switchId = data.getSwitchId();
        if (!switchRepository.exists(switchId)) {
            ErrorData errorData = new ErrorData(ErrorType.NOT_FOUND, format("Switch %s not found", switchId),
                    "Error when deleting switch rules");
            ErrorMessage errorMessage = new ErrorMessage(errorData, System.currentTimeMillis(), key);

            carrier.response(key, errorMessage);
            return;
        }
        Optional<SwitchProperties> switchProperties = switchPropertiesRepository.findBySwitchId(switchId);
        KildaFeatureToggles featureToggles = featureTogglesRepository.getOrDefault();
        boolean server42FlowRttFeatureToggle = featureToggles.getServer42FlowRtt();
        data.setServer42FlowRttFeatureToggle(server42FlowRttFeatureToggle);
        data.setServer42IslRttEnabled(featureToggles.getServer42IslRtt()
                && switchProperties.map(SwitchProperties::hasServer42IslRttEnabled).orElse(false));

        if (switchProperties.isPresent()) {
            data.setMultiTable(switchProperties.get().isMultiTable());
            data.setSwitchLldp(switchProperties.get().isSwitchLldp());
            data.setSwitchArp(switchProperties.get().isSwitchArp());
            data.setServer42FlowRttSwitchProperty(switchProperties.get().isServer42FlowRtt());
            data.setServer42Port(switchProperties.get().getServer42Port());
            data.setServer42Vlan(switchProperties.get().getServer42Vlan());
            data.setServer42MacAddress(switchProperties.get().getServer42MacAddress());
            Collection<FlowPath> flowPaths = flowPathRepository.findBySrcSwitch(switchId);
            List<Integer> flowPorts = new ArrayList<>();
            Set<Integer> flowLldpPorts = new HashSet<>();
            Set<Integer> flowArpPorts = new HashSet<>();
            Set<Integer> server42FlowPorts = new HashSet<>();
            fillFlowPorts(switchProperties.get(), flowPaths, flowPorts, flowLldpPorts, flowArpPorts, server42FlowPorts,
                    server42FlowRttFeatureToggle && switchProperties.get().isServer42FlowRtt());

            data.setFlowPorts(flowPorts);
            data.setFlowLldpPorts(flowLldpPorts);
            data.setFlowArpPorts(flowArpPorts);
            data.setServer42FlowRttPorts(server42FlowPorts);
            List<Integer> islPorts = islRepository.findBySrcSwitch(switchId).stream()
                    .map(isl -> isl.getSrcPort())
                    .collect(Collectors.toList());
            data.setIslPorts(islPorts);
        }
        carrier.sendCommandToSpeaker(key, data);
    }

    /**
     * Handle install rules request.
     */
    public void installRules(String key, SwitchRulesInstallRequest request) {
        isOperationCompleted = false;  // FIXME(surabujin): what it supposed to do? Can we get rid of it?
        SwitchId switchId = request.getSwitchId();
        if (!switchRepository.exists(switchId)) {
            ErrorData errorData = new ErrorData(ErrorType.NOT_FOUND, format("Switch %s not found", switchId),
                    "Error when installing switch rules");
            ErrorMessage errorMessage = new ErrorMessage(errorData, System.currentTimeMillis(), key);

            carrier.response(key, errorMessage);
            return;
        }

        Set<PathId> pathIds = flowPathRepository.findBySrcSwitch(switchId).stream()
                .map(FlowPath::getPathId)
                .collect(Collectors.toSet());
        DataAdapter dataAdapter = PersistenceDataAdapter.builder()
                .switchIds(Collections.singleton(switchId))
                .pathIds(pathIds)
                .persistenceManager(persistenceManager)
                .build();
        List<SpeakerData> speakerData = ruleManager.buildRulesForSwitch(switchId, dataAdapter);
        List<UUID> toInstall = speakerData.stream()
                .filter(buildPredicate(request.getInstallRulesAction()))
                .flatMap(data -> Stream.concat(Stream.of(data.getUuid()), data.getDependsOn().stream()))
                .collect(Collectors.toList());

        List<OfCommand> commands = speakerData.stream()
                .filter(data -> toInstall.contains(data.getUuid()))
                .map(this::toCommand)
                .collect(Collectors.toList());
        sentCommands.put(key, commands);

        carrier.sendOfCommandsToSpeaker(key, commands, OfCommandAction.INSTALL_IF_NOT_EXIST, switchId);
    }

    private Predicate<SpeakerData> buildPredicate(InstallRulesAction action) {
        switch (action) {
            case INSTALL_DROP:
                return buildPredicate(DROP_RULE_COOKIE);
            case INSTALL_BROADCAST:
                return buildPredicate(VERIFICATION_BROADCAST_RULE_COOKIE);
            case INSTALL_UNICAST:
                return buildPredicate(VERIFICATION_UNICAST_RULE_COOKIE);
            case INSTALL_DROP_VERIFICATION_LOOP:
                return buildPredicate(DROP_VERIFICATION_LOOP_RULE_COOKIE);
            case INSTALL_BFD_CATCH:
                return buildPredicate(CATCH_BFD_RULE_COOKIE);
            case INSTALL_ROUND_TRIP_LATENCY:
                return buildPredicate(ROUND_TRIP_LATENCY_RULE_COOKIE);
            case INSTALL_UNICAST_VXLAN:
                return buildPredicate(VERIFICATION_UNICAST_VXLAN_RULE_COOKIE);
            case INSTALL_MULTITABLE_PRE_INGRESS_PASS_THROUGH:
                return buildPredicate(MULTITABLE_PRE_INGRESS_PASS_THROUGH_COOKIE);
            case INSTALL_MULTITABLE_INGRESS_DROP:
                return buildPredicate(MULTITABLE_INGRESS_DROP_COOKIE);
            case INSTALL_MULTITABLE_POST_INGRESS_DROP:
                return buildPredicate(MULTITABLE_POST_INGRESS_DROP_COOKIE);
            case INSTALL_MULTITABLE_EGRESS_PASS_THROUGH:
                return buildPredicate(MULTITABLE_EGRESS_PASS_THROUGH_COOKIE);
            case INSTALL_MULTITABLE_TRANSIT_DROP:
                return buildPredicate(MULTITABLE_TRANSIT_DROP_COOKIE);
            case INSTALL_LLDP_INPUT_PRE_DROP:
                return buildPredicate(LLDP_INPUT_PRE_DROP_COOKIE);
            case INSTALL_LLDP_INGRESS:
                return buildPredicate(LLDP_INGRESS_COOKIE);
            case INSTALL_LLDP_POST_INGRESS:
                return buildPredicate(LLDP_POST_INGRESS_COOKIE);
            case INSTALL_LLDP_POST_INGRESS_VXLAN:
                return buildPredicate(LLDP_POST_INGRESS_VXLAN_COOKIE);
            case INSTALL_LLDP_POST_INGRESS_ONE_SWITCH:
                return buildPredicate(LLDP_POST_INGRESS_ONE_SWITCH_COOKIE);
            case INSTALL_LLDP_TRANSIT:
                return buildPredicate(LLDP_TRANSIT_COOKIE);
            case INSTALL_ARP_INPUT_PRE_DROP:
                return buildPredicate(ARP_INPUT_PRE_DROP_COOKIE);
            case INSTALL_ARP_INGRESS:
                return buildPredicate(ARP_INGRESS_COOKIE);
            case INSTALL_ARP_POST_INGRESS:
                return buildPredicate(ARP_POST_INGRESS_COOKIE);
            case INSTALL_ARP_POST_INGRESS_VXLAN:
                return buildPredicate(ARP_POST_INGRESS_VXLAN_COOKIE);
            case INSTALL_ARP_POST_INGRESS_ONE_SWITCH:
                return buildPredicate(ARP_POST_INGRESS_ONE_SWITCH_COOKIE);
            case INSTALL_ARP_TRANSIT:
                return buildPredicate(ARP_TRANSIT_COOKIE);
            case INSTALL_SERVER_42_TURNING:
            case INSTALL_SERVER_42_FLOW_RTT_TURNING:
                return buildPredicate(SERVER_42_FLOW_RTT_TURNING_COOKIE);
            case INSTALL_SERVER_42_OUTPUT_VLAN:
            case INSTALL_SERVER_42_FLOW_RTT_OUTPUT_VLAN:
                return buildPredicate(SERVER_42_FLOW_RTT_OUTPUT_VLAN_COOKIE);
            case INSTALL_SERVER_42_OUTPUT_VXLAN:
            case INSTALL_SERVER_42_FLOW_RTT_OUTPUT_VXLAN:
                return buildPredicate(SERVER_42_FLOW_RTT_OUTPUT_VXLAN_COOKIE);
            case INSTALL_SERVER_42_FLOW_RTT_VXLAN_TURNING:
                return buildPredicate(SERVER_42_FLOW_RTT_VXLAN_TURNING_COOKIE);
            case INSTALL_SERVER_42_ISL_RTT_TURNING:
                return buildPredicate(SERVER_42_ISL_RTT_TURNING_COOKIE);
            case INSTALL_SERVER_42_ISL_RTT_OUTPUT:
                return buildPredicate(SERVER_42_ISL_RTT_OUTPUT_COOKIE);
            case INSTALL_DEFAULTS:
                return this::allServiceRulesPredicate;
            default:
                throw new IllegalStateException(format("Unknown install rules action %s", action));
        }
    }

    private Predicate<SpeakerData> buildPredicate(long cookie) {
        return data -> {
            if (data instanceof FlowSpeakerData) {
                FlowSpeakerData flowSpeakerData = (FlowSpeakerData) data;
                return cookie == flowSpeakerData.getCookie().getValue();
            }
            return false;
        };
    }

    private boolean allServiceRulesPredicate(SpeakerData data) {
        if (data instanceof FlowSpeakerData) {
            FlowSpeakerData flowSpeakerData = (FlowSpeakerData) data;
            return flowSpeakerData.getCookie().getServiceFlag();
        }
        return false;
    }

    private OfCommand toCommand(SpeakerData speakerData) {
        if (speakerData instanceof FlowSpeakerData) {
            return new FlowCommand((FlowSpeakerData) speakerData);
        } else if (speakerData instanceof MeterSpeakerData) {
            return new MeterCommand((MeterSpeakerData) speakerData);
        } else if (speakerData instanceof GroupSpeakerData) {
            return new GroupCommand((GroupSpeakerData) speakerData);
        }
        throw new IllegalStateException(format("Unknown speaker data type %s", speakerData));
    }

    private void fillFlowPorts(SwitchProperties switchProperties, Collection<FlowPath> flowPaths,
                               List<Integer> flowPorts, Set<Integer> flowLldpPorts, Set<Integer> flowArpPorts,
                               Set<Integer> server42FlowPorts, boolean server42Rtt) {
        for (FlowPath flowPath : flowPaths) {
            if (flowPath.isForward()) {
                if (flowPath.isSrcWithMultiTable()) {
                    flowPorts.add(flowPath.getFlow().getSrcPort());
                    if (server42Rtt && !flowPath.getFlow().isOneSwitchFlow()) {
                        server42FlowPorts.add(flowPath.getFlow().getSrcPort());
                    }
                }
                if (flowPath.getFlow().getDetectConnectedDevices().isSrcLldp()
                        || switchProperties.isSwitchLldp()) {
                    flowLldpPorts.add(flowPath.getFlow().getSrcPort());
                }
                if (flowPath.getFlow().getDetectConnectedDevices().isSrcArp()
                        || switchProperties.isSwitchArp()) {
                    flowArpPorts.add(flowPath.getFlow().getSrcPort());
                }
            } else {
                if (flowPath.isDestWithMultiTable()) {
                    flowPorts.add(flowPath.getFlow().getDestPort());
                    if (server42Rtt && !flowPath.getFlow().isOneSwitchFlow()) {
                        server42FlowPorts.add(flowPath.getFlow().getDestPort());
                    }
                }
                if (flowPath.getFlow().getDetectConnectedDevices().isDstLldp()
                        || switchProperties.isSwitchLldp()) {
                    flowLldpPorts.add(flowPath.getFlow().getDestPort());
                }
                if (flowPath.getFlow().getDetectConnectedDevices().isDstArp()
                        || switchProperties.isSwitchArp()) {
                    flowArpPorts.add(flowPath.getFlow().getDestPort());
                }
            }
        }
    }

    private void handleRulesResponse(String key, SwitchRulesResponse response) {
        carrier.cancelTimeoutCallback(key);
        InfoMessage message = new InfoMessage(response, System.currentTimeMillis(), key);

        carrier.response(key, message);

        isOperationCompleted = true;  // FIXME(surabujin): what it supposed to do? Can we get rid of it?

        if (!active) {
            carrier.sendInactive();
        }
    }

    private void handleRulesResponse(String key, SpeakerCommandResponse response) {
        carrier.cancelTimeoutCallback(key);

        if (response.isSuccess()) {
            List<Long> rulesResponse = sentCommands.getOrDefault(key, Collections.emptyList()).stream()
                    .filter(command -> command instanceof FlowCommand)
                    .map(command -> (FlowCommand) command)
                    .map(FlowCommand::getData)
                    .map(FlowSpeakerData::getCookie)
                    .map(CookieBase::getValue)
                    .collect(Collectors.toList());
            SwitchRulesResponse switchRulesResponse = new SwitchRulesResponse(rulesResponse);
            InfoMessage message = new InfoMessage(switchRulesResponse, System.currentTimeMillis(), key);

            carrier.response(key, message);
        } else {
            carrier.errorResponse(key, ErrorType.INTERNAL_ERROR, "Failed to process rules",
                    response.getFailedCommandIds().values().stream().reduce("", String::concat));
        }
        sentCommands.put(key, null);

        isOperationCompleted = true;  // FIXME(surabujin): what it supposed to do? Can we get rid of it?

        if (!active) {
            carrier.sendInactive();
        }
    }

    @Override
    public void activate() {
        active = true;
    }

    @Override
    public boolean deactivate() {
        active = false;
        return isAllOperationsCompleted();
    }

    @Override
    public boolean isAllOperationsCompleted() {
        return isOperationCompleted;
    }
}
