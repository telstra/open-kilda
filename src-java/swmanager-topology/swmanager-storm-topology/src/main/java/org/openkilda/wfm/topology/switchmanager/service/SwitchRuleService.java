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
import static java.util.Collections.singletonList;
import static org.openkilda.wfm.topology.switchmanager.service.impl.PredicateBuilder.buildDeletePredicate;
import static org.openkilda.wfm.topology.switchmanager.service.impl.PredicateBuilder.buildInstallPredicate;
import static org.openkilda.wfm.topology.switchmanager.service.impl.PredicateBuilder.isInstallServiceRulesRequired;

import org.openkilda.floodlight.api.request.rulemanager.FlowCommand;
import org.openkilda.floodlight.api.request.rulemanager.GroupCommand;
import org.openkilda.floodlight.api.request.rulemanager.MeterCommand;
import org.openkilda.floodlight.api.request.rulemanager.OfCommand;
import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.messaging.MessageCookie;
import org.openkilda.messaging.command.switches.SwitchRulesDeleteRequest;
import org.openkilda.messaging.command.switches.SwitchRulesInstallRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.switches.SwitchRulesResponse;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.CookieBase;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.rulemanager.DataAdapter;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.MeterSpeakerData;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.adapter.PersistenceDataAdapter;
import org.openkilda.rulemanager.utils.RuleManagerHelper;
import org.openkilda.wfm.error.MessageDispatchException;
import org.openkilda.wfm.error.UnexpectedInputException;
import org.openkilda.wfm.topology.switchmanager.bolt.SwitchManagerHub.OfCommandAction;
import org.openkilda.wfm.topology.switchmanager.service.impl.CommandsBatch;
import org.openkilda.wfm.topology.switchmanager.service.impl.CommandsQueue;
import org.openkilda.wfm.topology.switchmanager.service.impl.PredicateBuilder;

import com.google.common.collect.Lists;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class SwitchRuleService implements SwitchManagerHubService {
    @Getter
    private SwitchManagerCarrier carrier;

    private FlowPathRepository flowPathRepository;
    private SwitchRepository switchRepository;
    private RuleManager ruleManager;
    private PersistenceManager persistenceManager;

    private boolean active = true;

    private boolean isOperationCompleted = true;

    private final Map<String, CommandsQueue> commandsCache = new HashMap<>();

    public SwitchRuleService(SwitchManagerCarrier carrier, PersistenceManager persistenceManager,
                             RuleManager ruleManager) {
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        flowPathRepository = repositoryFactory.createFlowPathRepository();
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
    public void dispatchWorkerMessage(InfoData payload, MessageCookie cookie)
            throws UnexpectedInputException, MessageDispatchException {
        if (payload instanceof SwitchRulesResponse) {
            handleRulesResponse(cookie.getValue(), (SwitchRulesResponse) payload);
        } else {
            throw new MessageDispatchException(cookie);
        }
    }

    @Override
    public void dispatchWorkerMessage(SpeakerResponse payload, MessageCookie cookie)
            throws MessageDispatchException, UnexpectedInputException {
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
    public void deleteRules(String key, SwitchRulesDeleteRequest request) {
        isOperationCompleted = false;  // FIXME(surabujin): what it supposed to do? Can we get rid of it?
        SwitchId switchId = request.getSwitchId();
        if (!switchRepository.exists(switchId)) {
            ErrorData errorData = new ErrorData(ErrorType.NOT_FOUND, format("Switch %s not found", switchId),
                    "Error when deleting switch rules");
            ErrorMessage errorMessage = new ErrorMessage(errorData, System.currentTimeMillis(), key);

            carrier.response(key, errorMessage);
            return;
        }

        if (request.getDeleteRulesAction() != null) {
            List<SpeakerData> speakerData = buildSpeakerData(switchId);
            List<UUID> toRemove = speakerData.stream()
                    .filter(buildDeletePredicate(request.getDeleteRulesAction()))
                    .map(SpeakerData::getUuid)
                    .collect(Collectors.toList());
            RuleManagerHelper.reverseDependencies(speakerData);
            List<OfCommand> removeCommands = speakerData.stream()
                    .filter(data -> toRemove.contains(data.getUuid()))
                    .map(this::toCommand)
                    .collect(Collectors.toList());
            CommandsBatch deleteCommandsBatch = new CommandsBatch(OfCommandAction.DELETE, removeCommands);
            List<CommandsBatch> commandsBatches = Lists.newArrayList(deleteCommandsBatch);
            if (isInstallServiceRulesRequired(request.getDeleteRulesAction())) {
                speakerData = buildSpeakerData(switchId);
                List<UUID> toInstall = speakerData.stream()
                        .filter(PredicateBuilder::allServiceRulesPredicate)
                        .flatMap(data -> Stream.concat(Stream.of(data.getUuid()), data.getDependsOn().stream()))
                        .collect(Collectors.toList());
                List<OfCommand> installCommands = speakerData.stream()
                        .filter(data -> toInstall.contains(data.getUuid()))
                        .map(this::toCommand)
                        .collect(Collectors.toList());
                CommandsBatch installCommandsBatch =
                        new CommandsBatch(OfCommandAction.INSTALL_IF_NOT_EXIST, installCommands);
                commandsBatches.add(installCommandsBatch);
            }
            CommandsQueue commandsQueue = new CommandsQueue(switchId, commandsBatches);
            commandsCache.put(key, commandsQueue);

            processCommandsBatch(key, commandsQueue);
        } else {
            carrier.sendCommandToSpeaker(key, request);
        }
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

        List<SpeakerData> speakerData = buildSpeakerData(switchId);
        List<UUID> toInstall = speakerData.stream()
                .filter(buildInstallPredicate(request.getInstallRulesAction()))
                .flatMap(data -> Stream.concat(Stream.of(data.getUuid()), data.getDependsOn().stream()))
                .collect(Collectors.toList());

        List<OfCommand> commands = speakerData.stream()
                .filter(data -> toInstall.contains(data.getUuid()))
                .map(this::toCommand)
                .collect(Collectors.toList());
        CommandsBatch commandsBatch = new CommandsBatch(OfCommandAction.INSTALL_IF_NOT_EXIST, commands);
        CommandsQueue commandsQueue = new CommandsQueue(switchId, singletonList(commandsBatch));
        commandsCache.put(key, commandsQueue);

        processCommandsBatch(key, commandsQueue);
    }

    private List<SpeakerData> buildSpeakerData(SwitchId switchId) {
        Set<PathId> pathIds = Stream.concat(flowPathRepository.findByEndpointSwitch(switchId).stream(),
                        flowPathRepository.findBySegmentSwitch(switchId).stream())
                .map(FlowPath::getPathId)
                .collect(Collectors.toSet());
        DataAdapter dataAdapter = PersistenceDataAdapter.builder()
                .switchIds(Collections.singleton(switchId))
                .pathIds(pathIds)
                .persistenceManager(persistenceManager)
                .build();
        return ruleManager.buildRulesForSwitch(switchId, dataAdapter);
    }

    private void processCommandsBatch(String key, CommandsQueue commandsQueue) {
        CommandsBatch commandsBatch = commandsQueue.getNext();
        log.info("Send {} command batch with key {}", commandsBatch.getAction(), key);
        carrier.sendOfCommandsToSpeaker(key, commandsBatch.getCommands(), commandsBatch.getAction(),
                commandsQueue.getSwitchId());
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

    private void handleRulesResponse(String key, SwitchRulesResponse response) {
        carrier.cancelTimeoutCallback(key);
        InfoMessage message = new InfoMessage(response, System.currentTimeMillis(), key);

        carrier.response(key, message);

        isOperationCompleted = true;  // FIXME(surabujin): what it supposed to do? Can we get rid of it?

        if (!active) {
            carrier.sendInactive();
        }
    }

    private void handleRulesResponse(String key, SpeakerCommandResponse response) throws UnexpectedInputException {
        CommandsQueue commandsQueue = commandsCache.get(key);
        if (commandsQueue == null) {
            throw new UnexpectedInputException(response);
        }

        if (response.isSuccess() && commandsQueue.hasNext()) {
            processCommandsBatch(key, commandsQueue);
        } else {
            sendResponse(key, commandsQueue, response);
        }
    }

    private void sendResponse(String key, CommandsQueue commandsQueue, SpeakerCommandResponse response) {
        carrier.cancelTimeoutCallback(key);

        if (response.isSuccess()) {
            List<Long> rulesResponse = commandsQueue.getFirst().stream()
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
                    String.join(" ", response.getFailedCommandIds().values()));
        }
        commandsCache.remove(key);

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
