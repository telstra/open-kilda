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
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateEvent.ERROR;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateEvent.METERS_RECEIVED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateEvent.METERS_UNSUPPORTED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateEvent.NEXT;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateEvent.RULES_RECEIVED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateEvent.TIMEOUT;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateState.FINISHED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateState.FINISHED_WITH_ERROR;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateState.INITIALIZED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateState.RECEIVE_DATA;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateState.VALIDATE_METERS;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateState.VALIDATE_RULES;

import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.switches.DumpMetersForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.DumpRulesForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.SwitchValidateRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.meter.MeterEntry;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.switches.MetersValidationEntry;
import org.openkilda.messaging.info.switches.RulesValidationEntry;
import org.openkilda.messaging.info.switches.SwitchValidationResponse;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.share.utils.AbstractBaseFsm;
import org.openkilda.wfm.topology.switchmanager.SwitchManagerCarrier;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateEvent;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateState;
import org.openkilda.wfm.topology.switchmanager.model.ValidateMetersResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateRulesResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidationResult;
import org.openkilda.wfm.topology.switchmanager.service.ValidationService;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class SwitchValidateFsm
        extends AbstractBaseFsm<SwitchValidateFsm, SwitchValidateState, SwitchValidateEvent, Object> {

    private static final String FINISHED_WITH_ERROR_METHOD_NAME = "finishedWithError";
    private static final String FINISHED_METHOD_NAME = "finished";
    private static final String ERROR_LOG_MESSAGE = "Key: {}, message: {}";

    private final String key;
    private final SwitchValidateRequest request;
    private final SwitchManagerCarrier carrier;
    private final ValidationService validationService;
    private SwitchId switchId;
    private boolean isSwitchSupportMeters = true;
    private List<FlowEntry> flowEntries;
    private List<MeterEntry> presentMeters;
    private ValidateRulesResult validateRulesResult;
    private ValidateMetersResult validateMetersResult;

    public SwitchValidateFsm(SwitchManagerCarrier carrier, String key, SwitchValidateRequest request,
                             ValidationService validationService) {
        this.carrier = carrier;
        this.key = key;
        this.request = request;
        this.validationService = validationService;
    }

    /**
     * FSM builder.
     */
    public static StateMachineBuilder<SwitchValidateFsm, SwitchValidateState,
            SwitchValidateEvent, Object> builder() {
        StateMachineBuilder<SwitchValidateFsm, SwitchValidateState, SwitchValidateEvent, Object> builder =
                StateMachineBuilderFactory.create(
                        SwitchValidateFsm.class,
                        SwitchValidateState.class,
                        SwitchValidateEvent.class,
                        Object.class,
                        SwitchManagerCarrier.class,
                        String.class,
                        SwitchValidateRequest.class,
                        ValidationService.class);

        builder.onEntry(INITIALIZED).callMethod("initialized");
        builder.externalTransition().from(INITIALIZED).to(RECEIVE_DATA).on(NEXT)
                .callMethod("receiveData");
        builder.internalTransition().within(RECEIVE_DATA).on(RULES_RECEIVED).callMethod("rulesReceived");
        builder.internalTransition().within(RECEIVE_DATA).on(METERS_RECEIVED).callMethod("metersReceived");
        builder.internalTransition().within(RECEIVE_DATA).on(METERS_UNSUPPORTED)
                .callMethod("metersUnsupported");

        builder.externalTransition().from(RECEIVE_DATA).to(FINISHED_WITH_ERROR).on(TIMEOUT)
                .callMethod("receivingDataFailedByTimeout");
        builder.externalTransition().from(RECEIVE_DATA).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);
        builder.externalTransition().from(RECEIVE_DATA).to(VALIDATE_RULES).on(NEXT)
                .callMethod("validateRules");

        builder.externalTransition().from(VALIDATE_RULES).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);
        builder.externalTransition().from(VALIDATE_RULES).to(VALIDATE_METERS).on(NEXT)
                .callMethod("validateMeters");

        builder.externalTransition().from(VALIDATE_METERS).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);
        builder.externalTransition().from(VALIDATE_METERS).to(FINISHED).on(NEXT)
                .callMethod(FINISHED_METHOD_NAME);

        return builder;
    }

    public String getKey() {
        return key;
    }

    protected void initialized(SwitchValidateState from, SwitchValidateState to,
                               SwitchValidateEvent event, Object context) {
        log.info("Key: {}, validate FSM initialized", key);
        switchId = request.getSwitchId();
    }

    protected void receiveData(SwitchValidateState from, SwitchValidateState to,
                               SwitchValidateEvent event, Object context) {
        log.info("Key: {}, sending requests to get switch rules and meters", key);

        carrier.sendCommand(key, new CommandMessage(new DumpRulesForSwitchManagerRequest(switchId),
                System.currentTimeMillis(), key));

        carrier.sendCommand(key, new CommandMessage(new DumpMetersForSwitchManagerRequest(switchId),
                System.currentTimeMillis(), key));
    }

    protected void rulesReceived(SwitchValidateState from, SwitchValidateState to,
                                 SwitchValidateEvent event, Object context) {
        log.info("Key: {}, switch rules received", key);
        this.flowEntries = (List<FlowEntry>) context;
        checkAllDataReceived();
    }

    protected void metersReceived(SwitchValidateState from, SwitchValidateState to,
                                  SwitchValidateEvent event, Object context) {
        log.info("Key: {}, switch meters received", key);
        this.presentMeters = (List<MeterEntry>) context;
        checkAllDataReceived();
    }

    protected void metersUnsupported(SwitchValidateState from, SwitchValidateState to,
                                  SwitchValidateEvent event, Object context) {
        log.info("Key: {}, switch meters unsupported", key);
        this.presentMeters = emptyList();
        this.isSwitchSupportMeters = false;
        checkAllDataReceived();
    }

    private void checkAllDataReceived() {
        if (flowEntries != null && presentMeters != null) {
            fire(NEXT);
        }
    }

    protected void receivingDataFailedByTimeout(SwitchValidateState from, SwitchValidateState to,
                                                 SwitchValidateEvent event, Object context) {
        ErrorData errorData = new ErrorData(ErrorType.OPERATION_TIMED_OUT, "Receiving data failed by timeout",
                "Error when receive switch data");
        ErrorMessage errorMessage = new ErrorMessage(errorData, System.currentTimeMillis(), key);

        log.warn(ERROR_LOG_MESSAGE, key, errorData.getErrorMessage());
        carrier.endProcessing(key);
        carrier.response(key, errorMessage);
    }

    protected void validateRules(SwitchValidateState from, SwitchValidateState to,
                                 SwitchValidateEvent event, Object context) {
        log.info("Key: {}, validate rules", key);
        try {
            Set<Long> presentCookies = flowEntries.stream()
                    .map(FlowEntry::getCookie)
                    .collect(Collectors.toSet());

            validateRulesResult = validationService.validateRules(switchId, presentCookies);

        } catch (Exception e) {
            sendException(e);
        }
    }

    protected void validateMeters(SwitchValidateState from, SwitchValidateState to,
                                  SwitchValidateEvent event, Object context) {
        try {
            if (isSwitchSupportMeters) {
                log.info("Key: {}, validate meters", key);
                validateMetersResult = validationService.validateMeters(switchId, presentMeters,
                        carrier.getFlowMeterMinBurstSizeInKbits(), carrier.getFlowMeterBurstCoefficient());
            }

        } catch (Exception e) {
            sendException(e);
        }
    }

    protected void finished(SwitchValidateState from, SwitchValidateState to,
                            SwitchValidateEvent event, Object context) {
        if (request.isPerformSync()) {
            carrier.runSwitchSync(key, request,
                    new ValidationResult(flowEntries, validateRulesResult, validateMetersResult));
        } else {
            RulesValidationEntry rulesValidationEntry = new RulesValidationEntry(validateRulesResult.getMissingRules(),
                    validateRulesResult.getProperRules(), validateRulesResult.getExcessRules());

            MetersValidationEntry metersValidationEntry = null;
            if (isSwitchSupportMeters) {
                metersValidationEntry = new MetersValidationEntry(
                        validateMetersResult.getMissingMeters(), validateMetersResult.getMisconfiguredMeters(),
                        validateMetersResult.getProperMeters(), validateMetersResult.getExcessMeters());
            }

            SwitchValidationResponse response = new SwitchValidationResponse(
                    rulesValidationEntry, metersValidationEntry);
            InfoMessage message = new InfoMessage(response, System.currentTimeMillis(), key);
            carrier.endProcessing(key);
            carrier.response(key, message);
        }
    }

    protected void finishedWithError(SwitchValidateState from, SwitchValidateState to,
                                     SwitchValidateEvent event, Object context) {
        ErrorMessage message = (ErrorMessage) context;
        log.error(ERROR_LOG_MESSAGE, key, message.getData().getErrorMessage());
        carrier.endProcessing(key);
        carrier.response(key, message);
    }

    private void sendException(Exception e) {
        ErrorData errorData = new ErrorData(ErrorType.INTERNAL_ERROR, e.getMessage(),
                "Error in SwitchValidateFsm");
        ErrorMessage errorMessage = new ErrorMessage(errorData, System.currentTimeMillis(), key);
        fire(ERROR, errorMessage);
    }

    public enum SwitchValidateState {
        INITIALIZED,
        RECEIVE_DATA,
        VALIDATE_RULES,
        VALIDATE_METERS,
        FINISHED_WITH_ERROR,
        FINISHED
    }

    public enum SwitchValidateEvent {
        NEXT,
        RULES_RECEIVED,
        METERS_RECEIVED,
        METERS_UNSUPPORTED,
        TIMEOUT,
        ERROR
    }
}
