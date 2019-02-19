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

import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateEvent.ERROR;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateEvent.METERS_RECEIVED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateEvent.NEXT;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateEvent.RULES_RECEIVED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateEvent.TIMEOUT;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateState.FINISHED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateState.FINISHED_WITH_ERROR;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateState.INITIALIZED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateState.RECEIVE_METERS;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateState.RECEIVE_RULES;
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
import org.openkilda.messaging.info.switches.MetersValidationEntry;
import org.openkilda.messaging.info.switches.RulesValidationEntry;
import org.openkilda.messaging.info.switches.SwitchValidationResponse;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.switchmanager.SwitchValidationCarrier;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateEvent;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateState;
import org.openkilda.wfm.topology.switchmanager.model.ValidateMetersResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateRulesResult;
import org.openkilda.wfm.topology.switchmanager.service.ValidationService;
import org.openkilda.wfm.topology.switchmanager.service.impl.ValidationServiceImpl;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;
import org.squirrelframework.foundation.fsm.impl.AbstractStateMachine;

import java.util.List;
import java.util.Set;

@Slf4j
public class SwitchValidateFsm
        extends AbstractStateMachine<SwitchValidateFsm, SwitchValidateState, SwitchValidateEvent, Object> {

    private static final String FINISHED_WITH_ERROR_METHOD_NAME = "finishedWithError";
    private static final String FINISHED_METHOD_NAME = "finished";
    private static final String ERROR_LOG_MESSAGE = "Key: {}, message: {}";

    private final String key;
    private final SwitchValidateRequest request;
    private final SwitchValidationCarrier carrier;
    private final PersistenceManager persistenceManager;
    private SwitchId switchId;
    private Set<Long> presentCookies;
    private List<MeterEntry> presentMeters;
    private ValidateRulesResult validateRulesResult;
    private ValidateMetersResult validateMetersResult;

    public SwitchValidateFsm(SwitchValidationCarrier carrier, String key, SwitchValidateRequest request,
                             PersistenceManager persistenceManager) {
        this.carrier = carrier;
        this.key = key;
        this.request = request;
        this.persistenceManager = persistenceManager;
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
                        SwitchValidationCarrier.class,
                        String.class,
                        SwitchValidateRequest.class,
                        PersistenceManager.class);

        builder.onEntry(INITIALIZED).callMethod("initialized");
        builder.externalTransition().from(INITIALIZED).to(RECEIVE_RULES).on(NEXT)
                .callMethod("receiveRules");
        builder.internalTransition().within(RECEIVE_RULES).on(RULES_RECEIVED).callMethod("rulesReceived");

        builder.externalTransition().from(RECEIVE_RULES).to(FINISHED_WITH_ERROR).on(TIMEOUT)
                .callMethod("receivingRulesFailedByTimeout");
        builder.externalTransition().from(RECEIVE_RULES).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);
        builder.externalTransition().from(RECEIVE_RULES).to(VALIDATE_RULES).on(NEXT)
                .callMethod("validateRules");

        builder.externalTransition().from(VALIDATE_RULES).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);
        builder.externalTransition().from(VALIDATE_RULES).to(RECEIVE_METERS).on(NEXT)
                .callMethod("receiveMeters");

        builder.internalTransition().within(RECEIVE_METERS).on(METERS_RECEIVED).callMethod("metersReceived");

        builder.externalTransition().from(RECEIVE_METERS).to(FINISHED_WITH_ERROR).on(TIMEOUT)
                .callMethod("receivingMetersFailedByTimeout");
        builder.externalTransition().from(RECEIVE_METERS).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);
        builder.externalTransition().from(RECEIVE_METERS).to(VALIDATE_METERS).on(NEXT)
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
        log.info("Key: {}, FSM initialized", key);
        switchId = request.getSwitchId();
    }

    protected void receiveRules(SwitchValidateState from, SwitchValidateState to,
                                SwitchValidateEvent event, Object context) {
        log.info("Key: {}, request to get switch rules has been sent", key);
        CommandMessage dumpCommandMessage = new CommandMessage(new DumpRulesForSwitchManagerRequest(switchId),
                System.currentTimeMillis(), key);

        carrier.sendCommand(key, dumpCommandMessage);
    }

    protected void rulesReceived(SwitchValidateState from, SwitchValidateState to,
                                 SwitchValidateEvent event, Object context) {
        log.info("Key: {}, switch rules received", key);
        this.presentCookies = (Set<Long>) context;
        fire(NEXT);
    }

    protected void receivingRulesFailedByTimeout(SwitchValidateState from, SwitchValidateState to,
                                                 SwitchValidateEvent event, Object context) {
        ErrorData errorData = new ErrorData(ErrorType.OPERATION_TIMED_OUT, "Receiving rules failed by timeout",
                "Error when receive switch rules");
        ErrorMessage errorMessage = new ErrorMessage(errorData, System.currentTimeMillis(), key);

        log.warn(ERROR_LOG_MESSAGE, key, errorData.getErrorMessage());
        carrier.endProcessing(key);
        carrier.response(key, errorMessage);
    }

    protected void validateRules(SwitchValidateState from, SwitchValidateState to,
                                 SwitchValidateEvent event, Object context) {
        log.info("Key: {}, validate rules", key);
        try {
            ValidationService validationService = new ValidationServiceImpl(persistenceManager);
            validateRulesResult = validationService.validateRules(switchId, presentCookies);

        } catch (Exception e) {
            sendException(e);
        }
    }

    protected void receiveMeters(SwitchValidateState from, SwitchValidateState to,
                                 SwitchValidateEvent event, Object context) {
        log.info("Key: {}, request to get switch meters has been sent", key);
        CommandMessage dumpCommandMessage = new CommandMessage(new DumpMetersForSwitchManagerRequest(switchId),
                System.currentTimeMillis(), key);

        carrier.sendCommand(key, dumpCommandMessage);
    }

    protected void metersReceived(SwitchValidateState from, SwitchValidateState to,
                                  SwitchValidateEvent event, Object context) {
        log.info("Key: {}, switch meters received", key);
        this.presentMeters = (List<MeterEntry>) context;
        fire(NEXT);
    }

    protected void receivingMetersFailedByTimeout(SwitchValidateState from, SwitchValidateState to,
                                                  SwitchValidateEvent event, Object context) {
        ErrorData errorData = new ErrorData(ErrorType.OPERATION_TIMED_OUT, "Receiving meters failed by timeout",
                "Error when receive switch meters");
        ErrorMessage errorMessage = new ErrorMessage(errorData, System.currentTimeMillis(), key);

        log.warn(ERROR_LOG_MESSAGE, key, errorData.getErrorMessage());
        carrier.endProcessing(key);
        carrier.response(key, errorMessage);
    }

    protected void validateMeters(SwitchValidateState from, SwitchValidateState to,
                                  SwitchValidateEvent event, Object context) {
        log.info("Key: {}, validate rules", key);
        try {
            ValidationService validationService = new ValidationServiceImpl(persistenceManager);
            validateMetersResult = validationService.validateMeters(switchId, presentMeters,
                    carrier.getFlowMeterMinBurstSizeInKbits(), carrier.getFlowMeterBurstCoefficient());

        } catch (Exception e) {
            sendException(e);
        }
    }

    protected void finished(SwitchValidateState from, SwitchValidateState to,
                            SwitchValidateEvent event, Object context) {
        RulesValidationEntry rulesValidationEntry = new RulesValidationEntry(validateRulesResult.getMissingRules(),
                validateRulesResult.getProperRules(), validateRulesResult.getExcessRules());
        MetersValidationEntry metersValidationEntry = new MetersValidationEntry(validateMetersResult.getMissingMeters(),
                validateMetersResult.getMisconfiguredMeters(), validateMetersResult.getProperMeters(),
                validateMetersResult.getExcessMeters());
        SwitchValidationResponse response = new SwitchValidationResponse(rulesValidationEntry, metersValidationEntry);
        InfoMessage message = new InfoMessage(response, System.currentTimeMillis(), key);
        carrier.endProcessing(key);
        carrier.response(key, message);
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
        RECEIVE_RULES,
        VALIDATE_RULES,
        RECEIVE_METERS,
        VALIDATE_METERS,
        FINISHED_WITH_ERROR,
        FINISHED
    }

    public enum SwitchValidateEvent {
        NEXT,
        RULES_RECEIVED,
        METERS_RECEIVED,
        TIMEOUT,
        ERROR
    }
}
