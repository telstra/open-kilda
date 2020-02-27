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

package org.openkilda.wfm.topology.switchmanager.fsm;

import static java.util.Collections.emptyList;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.ERROR;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.METERS_REMOVED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.NEXT;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.RULES_REINSTALLED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.RULES_REMOVED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.RULES_SYNCHRONIZED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.SPEAKER_RESPONSE;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.TIMEOUT;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.FINISHED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.FINISHED_WITH_ERROR;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.INITIALIZED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.METERS_COMMANDS_SEND;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.RULES_COMMANDS_SEND;

import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.api.request.factory.FlowSegmentRequestFactory;
import org.openkilda.floodlight.api.response.SpeakerFlowSegmentResponse;
import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.messaging.command.flow.ReinstallDefaultFlowForSwitchManagerRequest;
import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.messaging.command.flow.RemoveFlowForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.DeleterMeterForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.SwitchValidateRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.rule.SwitchSyncErrorData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowReinstallResponse;
import org.openkilda.messaging.info.switches.MeterInfoEntry;
import org.openkilda.messaging.info.switches.MetersSyncEntry;
import org.openkilda.messaging.info.switches.RulesSyncEntry;
import org.openkilda.messaging.info.switches.SwitchSyncResponse;
import org.openkilda.model.Cookie;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.share.utils.AbstractBaseFsm;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState;
import org.openkilda.wfm.topology.switchmanager.model.SwitchValidationContext;
import org.openkilda.wfm.topology.switchmanager.model.ValidateMetersResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateRulesResult;
import org.openkilda.wfm.topology.switchmanager.service.CommandBuilder;
import org.openkilda.wfm.topology.switchmanager.service.SwitchManagerCarrier;
import org.openkilda.wfm.topology.switchmanager.utils.FlowSegmentRequestMeterIdExtractor;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public class SwitchSyncFsm extends AbstractBaseFsm<SwitchSyncFsm, SwitchSyncState, SwitchSyncEvent, Object> {

    private static final String FINISHED_WITH_ERROR_METHOD_NAME = "finishedWithError";
    private static final String FINISHED_METHOD_NAME = "finished";
    private static final String ERROR_LOG_MESSAGE = "Key: {}, message: {}";

    private final NoArgGenerator commandIdGenerator = Generators.timeBasedGenerator();

    private final CommandBuilder commandBuilder;

    private final String key;
    private final SwitchValidateRequest request;
    private final SwitchManagerCarrier carrier;
    private SwitchValidationContext validationContext;
    private Set<Long> removeDefaultRules = new HashSet<>();

    private final List<FlowSegmentRequest> missingFlowSegmentsInstallRequests = new ArrayList<>();
    private final List<RemoveFlow> excessRules = new ArrayList<>();
    private final List<Long> excessMeters = new ArrayList<>();

    private final Set<UUID> pendingRequests = new HashSet<>();
    private final Set<Cookie> installedOfFlowCookies = new HashSet<>();

    private int excessRulesPendingResponsesCount = 0;
    private int reinstallDefaultRulesPendingResponsesCount = 0;
    private int excessMetersPendingResponsesCount = 0;

    public SwitchSyncFsm(SwitchManagerCarrier carrier, String key, CommandBuilder commandBuilder,
                         SwitchValidateRequest request, SwitchValidationContext validationContext) {
        this.carrier = carrier;
        this.key = key;
        this.request = request;
        this.validationContext = validationContext;

        this.commandBuilder = commandBuilder;
    }

    /**
     * FSM builder.
     */
    public static StateMachineBuilder<SwitchSyncFsm, SwitchSyncState, SwitchSyncEvent, Object> builder() {
        StateMachineBuilder<SwitchSyncFsm, SwitchSyncState, SwitchSyncEvent, Object> builder =
                StateMachineBuilderFactory.create(
                        SwitchSyncFsm.class,
                        SwitchSyncState.class,
                        SwitchSyncEvent.class,
                        Object.class,
                        SwitchManagerCarrier.class,
                        String.class,
                        CommandBuilder.class,
                        SwitchValidateRequest.class,
                        SwitchValidationContext.class);

        builder.onEntry(INITIALIZED).callMethod("initialized");
        builder.externalTransition().from(INITIALIZED).to(RULES_COMMANDS_SEND).on(NEXT)
                .callMethod("sendRulesCommands");
        builder.externalTransition().from(INITIALIZED).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);

        builder.internalTransition().within(RULES_COMMANDS_SEND).on(RULES_REMOVED).callMethod("ruleRemoved");
        builder.internalTransition().within(RULES_COMMANDS_SEND)
                .on(SPEAKER_RESPONSE).callMethod("speakerResponseForRulesSync");
        builder.internalTransition().within(RULES_COMMANDS_SEND).on(RULES_REINSTALLED)
                .callMethod("defaultRuleReinstalled");

        builder.externalTransition().from(RULES_COMMANDS_SEND).to(FINISHED_WITH_ERROR).on(TIMEOUT)
                .callMethod("commandsProcessingFailedByTimeout");
        builder.externalTransition().from(RULES_COMMANDS_SEND).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);

        builder.externalTransition().from(RULES_COMMANDS_SEND).to(METERS_COMMANDS_SEND).on(RULES_SYNCHRONIZED)
                .callMethod("sendMetersCommands");
        builder.internalTransition().within(METERS_COMMANDS_SEND).on(METERS_REMOVED).callMethod("meterRemoved");

        builder.externalTransition().from(METERS_COMMANDS_SEND).to(FINISHED_WITH_ERROR).on(TIMEOUT)
                .callMethod("commandsProcessingFailedByTimeout");
        builder.externalTransition().from(METERS_COMMANDS_SEND).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);
        builder.externalTransition().from(METERS_COMMANDS_SEND).to(FINISHED).on(NEXT)
                .callMethod(FINISHED_METHOD_NAME);

        return builder;
    }

    public String getKey() {
        return key;
    }

    protected void initialized(SwitchSyncState from, SwitchSyncState to,
                               SwitchSyncEvent event, Object context) {
        log.info("The switch sync process for {} has been started (key={})", validationContext.getSwitchId(), key);

        try {
            makeInstallMissingFlowSegmentSpeakerRequests();
            makeRemoveExcessOfFlowSpeakerRequests();
            makeRemoveExcessMeterSpeakerRequests();
        } catch (Exception e) {
            fireError(e);
        }
    }

    protected void sendRulesCommands(SwitchSyncState from, SwitchSyncState to, SwitchSyncEvent event, Object context) {
        sendMissingSegmentsInstallRequests();
        sendExcessOfFlowRemoveRequests();
        sendMissingDefaultOfFlowInstallRequests();

        continueIfRulesSynchronized();
    }

    protected void sendMetersCommands(SwitchSyncState from, SwitchSyncState to,
                                      SwitchSyncEvent event, Object context) {
        SwitchId switchId = validationContext.getSwitchId();
        if (excessMeters.isEmpty()) {
            log.info("Nothing to do with meters (switch={}, key={})", switchId, key);
            fire(NEXT);
        } else {
            log.info("Request to remove switch meters has been sent (switch={}, key={})", switchId, key);
            excessMetersPendingResponsesCount = excessMeters.size();

            for (Long meterId : excessMeters) {
                carrier.sendCommandToSpeaker(key, new DeleterMeterForSwitchManagerRequest(switchId, meterId));
            }
        }
    }

    protected void ruleRemoved(SwitchSyncState from, SwitchSyncState to,
                               SwitchSyncEvent event, Object context) {
        log.info("Switch rule removed (switch={}, key={})", validationContext.getSwitchId(), key);
        excessRulesPendingResponsesCount--;
        continueIfRulesSynchronized();
    }

    protected void speakerResponseForRulesSync(
            SwitchSyncState from, SwitchSyncState to, SwitchSyncEvent event, Object context) {
        SpeakerResponse response = (SpeakerResponse) context;  // FIXME(surabujin)

        if (response instanceof SpeakerFlowSegmentResponse) {
            speakerResponseForRulesSync((SpeakerFlowSegmentResponse) response);
        } else {
            log.error("Ignoring unexpected speaker response {}", response);
        }

        continueIfRulesSynchronized();
    }

    private void speakerResponseForRulesSync(SpeakerFlowSegmentResponse response) {
        pendingRequests.remove(response.getCommandId());
        if (response.isSuccess()) {
            installedOfFlowCookies.add(response.getCookie());
        } else {
            fireError(String.format("Receive error response %s for missing flow segment install request", response));
        }
    }

    protected void defaultRuleReinstalled(SwitchSyncState from, SwitchSyncState to,
                                          SwitchSyncEvent event, Object context) {
        log.info("Default switch rule reinstalled (switch={}, key={})", validationContext.getSwitchId(), key);
        FlowReinstallResponse response = (FlowReinstallResponse) context;

        Long removedRule = response.getRemovedRule();
        if (removedRule != null) {
            removeDefaultRules.add(removedRule);
        }

        Long installedRule = response.getInstalledRule();
        if (installedRule != null) {
            installedOfFlowCookies.add(new Cookie(installedRule));
        }

        reinstallDefaultRulesPendingResponsesCount--;
        continueIfRulesSynchronized();
    }

    protected void meterRemoved(SwitchSyncState from, SwitchSyncState to,
                                SwitchSyncEvent event, Object context) {
        log.info("Switch meter removed (switch={}, key={})", validationContext.getSwitchId(), key);
        excessMetersPendingResponsesCount--;
        continueIfMetersCommandsDone();
    }

    private void continueIfRulesSynchronized() {
        if (pendingRequests.isEmpty()
                && excessRulesPendingResponsesCount == 0
                && reinstallDefaultRulesPendingResponsesCount == 0) {
            fire(RULES_SYNCHRONIZED);
        }
    }

    private void continueIfMetersCommandsDone() {
        if (excessMetersPendingResponsesCount == 0) {
            fire(NEXT);
        }
    }

    protected void commandsProcessingFailedByTimeout(SwitchSyncState from, SwitchSyncState to,
                                                     SwitchSyncEvent event, Object context) {
        ErrorData errorData = new ErrorData(ErrorType.OPERATION_TIMED_OUT, "Commands processing failed by timeout",
                "Error when processing switch commands");
        ErrorMessage errorMessage = new ErrorMessage(errorData, System.currentTimeMillis(), key);

        log.warn(ERROR_LOG_MESSAGE, key, errorData.getErrorMessage());
        carrier.response(key, errorMessage);
    }

    protected void finished(SwitchSyncState from, SwitchSyncState to,
                            SwitchSyncEvent event, Object context) {
        ValidateRulesResult validateRulesResult = validationContext.getOfFlowsValidationReport();
        ValidateMetersResult validateMetersResult = validationContext.getMetersValidationReport();

        List<Long> installed = installedOfFlowCookies.stream()
                .map(Cookie::getValue)
                .collect(Collectors.toList());

        List<Long> removed = new ArrayList<>();
        if (request.isRemoveExcess()) {
            excessRules.stream()
                    .map(RemoveFlow::getCookie)
                    .forEach(removed::add);
        }
        removed.addAll(removeDefaultRules);

        RulesSyncEntry rulesEntry = new RulesSyncEntry(validateRulesResult.getMissingRules(),
                validateRulesResult.getMisconfiguredRules(),
                validateRulesResult.getProperRules(),
                validateRulesResult.getExcessRules(),
                installed, removed);

        MetersSyncEntry metersEntry = null;
        if (validationContext.getMetersValidationReport() != null) {
            metersEntry = new MetersSyncEntry(validateMetersResult.getMissingMeters(),
                    validateMetersResult.getMisconfiguredMeters(),
                    validateMetersResult.getProperMeters(),
                    validateMetersResult.getExcessMeters(),
                    validateMetersResult.getMissingMeters(),
                    request.isRemoveExcess() ? validateMetersResult.getExcessMeters() : emptyList());
        }

        SwitchSyncResponse response = new SwitchSyncResponse(validationContext.getSwitchId(), rulesEntry, metersEntry);
        InfoMessage message = new InfoMessage(response, System.currentTimeMillis(), key);

        carrier.cancelTimeoutCallback(key);
        carrier.response(key, message);
    }

    protected void finishedWithError(SwitchSyncState from, SwitchSyncState to,
                                     SwitchSyncEvent event, Object context) {
        ErrorMessage sourceError = (ErrorMessage) context;
        ErrorMessage message = new ErrorMessage(sourceError.getData(), System.currentTimeMillis(), key);

        log.error(ERROR_LOG_MESSAGE, key, message.getData().getErrorMessage());

        carrier.cancelTimeoutCallback(key);
        carrier.response(key, message);
    }

    private void fireError(Exception e) {
        fireError(e.getMessage());
    }

    private void fireError(String message) {
        log.error(message);

        ErrorData errorData = new SwitchSyncErrorData(
                validationContext.getSwitchId(), ErrorType.INTERNAL_ERROR, message, "Error in SwitchSyncFsm");
        ErrorMessage errorMessage = new ErrorMessage(errorData, System.currentTimeMillis(), key);
        fire(ERROR, errorMessage);
    }

    private void makeInstallMissingFlowSegmentSpeakerRequests() {
        ValidateRulesResult ofFlowsReport = validationContext.getOfFlowsValidationReport();
        if (ofFlowsReport == null || ofFlowsReport.getMissingRules().isEmpty()) {
            return;
        }

        Set<Cookie> missing = ofFlowsReport.getMissingRules().stream()
                .map(Cookie::new)
                .collect(Collectors.toCollection(HashSet::new));
        Iterator<FlowSegmentRequestFactory> iter = validationContext.getExpectedFlowSegments().iterator();
        while (! missing.isEmpty() && iter.hasNext()) {
            FlowSegmentRequestFactory entry = iter.next();
            if (missing.remove(entry.getCookie())) {
                Optional<? extends FlowSegmentRequest> potentialRequest = entry.makeInstallRequest(
                        commandIdGenerator.generate());
                if (potentialRequest.isPresent()) {
                    missingFlowSegmentsInstallRequests.add(potentialRequest.get());
                } else {
                    // TODO(surabujin): we should try to install all available segments (i.e. this error should not
                    //  stop sync process)
                    throw new IllegalStateException(String.format(
                            "Unable to create install request for missing OF flow (sw=%s, cookie=%s, flowId=%s)",
                            entry.getSwitchId(), entry.getCookie(), entry.getFlowId()));
                }
            }
        }
    }

    protected void makeRemoveExcessOfFlowSpeakerRequests() {
        if (!request.isRemoveExcess()) {
            return;
        }
        excessRules.addAll(commandBuilder.buildCommandsToRemoveExcessRules(
                validationContext.getSwitchId(), validationContext.getActualOfFlows(),
                validationContext.getOfFlowsValidationReport().getExcessRules()));
    }

    protected void makeRemoveExcessMeterSpeakerRequests() {
        if (! request.isRemoveExcess() || validationContext.getMetersValidationReport() == null) {
            return;
        }

        ValidateMetersResult validateMetersResult = validationContext.getMetersValidationReport();

        // do not produce meter remove requests for missing(and planned to install) flow segments
        // or just installed flow segment will be removed together with meter
        Set<Long> missingSegmentMeters = collectMetersForMissingSegments(missingFlowSegmentsInstallRequests).stream()
                .map(MeterId::getValue)
                .collect(Collectors.toCollection(HashSet::new));
        validateMetersResult.getExcessMeters().stream()
                .filter(Objects::nonNull)  // FIXME(surabujin): do we really need it?
                .map(MeterInfoEntry::getMeterId)
                .filter(entry -> entry != null && ! missingSegmentMeters.remove(entry))
                .forEach(excessMeters::add);
    }

    private Set<MeterId> collectMetersForMissingSegments(List<FlowSegmentRequest> missingSegmentRequests) {
        FlowSegmentRequestMeterIdExtractor meterExtractor = new FlowSegmentRequestMeterIdExtractor();
        for (FlowSegmentRequest request : missingSegmentRequests) {
            meterExtractor.handle(request);
        }
        return meterExtractor.getSeenMeterId();
    }

    private void sendMissingSegmentsInstallRequests() {
        if (missingFlowSegmentsInstallRequests.isEmpty()) {
            return;
        }

        log.info("Key: {}, request to install switch rules has been sent", key);
        for (FlowSegmentRequest entry : missingFlowSegmentsInstallRequests) {
            carrier.sendCommandToSpeaker(key, entry);
            pendingRequests.add(entry.getCommandId());
        }
    }

    private void sendExcessOfFlowRemoveRequests() {
        if (excessRules.isEmpty()) {
            return;
        }

        log.info("Key: {}, request to remove switch rules has been sent", key);
        excessRulesPendingResponsesCount = excessRules.size();
        for (RemoveFlow command : excessRules) {
            carrier.sendCommandToSpeaker(key, new RemoveFlowForSwitchManagerRequest(
                    validationContext.getSwitchId(), command));
        }
    }

    private void sendMissingDefaultOfFlowInstallRequests() {
        ValidateRulesResult validateRulesResult = validationContext.getOfFlowsValidationReport();
        List<Long> missingDefaultOfFlows = validateRulesResult.getMisconfiguredRules().stream()
                .filter(Cookie::isDefaultRule)
                .collect(Collectors.toList());

        if (missingDefaultOfFlows.isEmpty()) {
            return;
        }

        log.info("Key: {}, request to reinstall default switch rules has been sent", key);
        reinstallDefaultRulesPendingResponsesCount = missingDefaultOfFlows.size();

        SwitchId switchId = validationContext.getSwitchId();
        for (Long rule : missingDefaultOfFlows) {
            carrier.sendCommandToSpeaker(key, new ReinstallDefaultFlowForSwitchManagerRequest(switchId, rule));
        }
    }

    public enum SwitchSyncState {
        INITIALIZED,
        RULES_COMMANDS_SEND,
        METERS_COMMANDS_SEND,
        FINISHED_WITH_ERROR,
        FINISHED
    }

    public enum SwitchSyncEvent {
        NEXT,
        RULES_REMOVED,
        RULES_REINSTALLED,
        RULES_SYNCHRONIZED,
        SPEAKER_RESPONSE,
        METERS_REMOVED,
        TIMEOUT,
        ERROR,
        FINISH
    }
}
