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

package org.openkilda.wfm.topology.switchmanager.service;

import org.openkilda.messaging.MessageCookie;
import org.openkilda.messaging.command.switches.SwitchValidateRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.flow.FlowDumpResponse;
import org.openkilda.messaging.info.group.GroupDumpResponse;
import org.openkilda.messaging.info.grpc.DumpLogicalPortsResponse;
import org.openkilda.messaging.info.meter.MeterDumpResponse;
import org.openkilda.messaging.info.meter.SwitchMeterUnsupported;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.error.MessageDispatchException;
import org.openkilda.wfm.error.UnexpectedInputException;
import org.openkilda.wfm.share.metrics.MeterRegistryHolder;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.switchmanager.error.SpeakerFailureException;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateContext;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateEvent;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateState;
import org.openkilda.wfm.topology.switchmanager.model.SwitchEntities;

import com.google.common.annotations.VisibleForTesting;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.LongTaskTimer.Sample;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SwitchValidateService implements SwitchManagerHubService {
    @Getter
    private final SwitchManagerCarrier carrier;

    private final Map<String, SwitchValidateFsm> handlers = new HashMap<>();

    private final ValidationService validationService;
    private final StateMachineBuilder<
            SwitchValidateFsm, SwitchValidateState, SwitchValidateEvent, SwitchValidateContext> builder;
    private final FsmExecutor<
            SwitchValidateFsm, SwitchValidateState, SwitchValidateEvent, SwitchValidateContext> fsmExecutor;

    private final PersistenceManager persistenceManager;

    @Getter
    private boolean active = true;

    public SwitchValidateService(
            SwitchManagerCarrier carrier, PersistenceManager persistenceManager, ValidationService validationService) {
        this.carrier = carrier;
        this.builder = SwitchValidateFsm.builder();
        this.fsmExecutor = new FsmExecutor<>(SwitchValidateEvent.NEXT);
        this.validationService = validationService;
        this.persistenceManager = persistenceManager;
    }

    @Override
    public void timeout(@NonNull MessageCookie cookie) throws MessageDispatchException {
        handle(cookie, SwitchValidateEvent.ERROR, SwitchValidateContext.builder().build());
    }

    @Override
    public void dispatchWorkerMessage(InfoData payload, MessageCookie cookie)
            throws UnexpectedInputException, MessageDispatchException {
        if (payload instanceof FlowDumpResponse) {
            handleFlowEntriesResponse((FlowDumpResponse) payload, cookie);
        } else if (payload instanceof GroupDumpResponse) {
            handleGroupEntriesResponse((GroupDumpResponse) payload, cookie);
        } else if (payload instanceof DumpLogicalPortsResponse) {
            handleLogicalPortResponse((DumpLogicalPortsResponse) payload, cookie);
        } else if (payload instanceof MeterDumpResponse) {
            handleMeterEntriesResponse((MeterDumpResponse) payload, cookie);
        } else if (payload instanceof SwitchMeterUnsupported) {
            handleMetersUnsupportedResponse(cookie);
        } else if (payload instanceof SwitchEntities) {
            handleExpectedSwitchEntities((SwitchEntities) payload, cookie);
        } else {
            throw new UnexpectedInputException(payload);
        }
    }

    @Override
    public void dispatchErrorMessage(ErrorData payload, MessageCookie cookie) throws MessageDispatchException {
        SwitchValidateContext context = SwitchValidateContext.builder()
                .error(new SpeakerFailureException(payload))
                .build();
        handle(cookie, SwitchValidateEvent.ERROR_RECEIVED, context);
    }

    @Override
    public void dispatchHeavyOperationMessage(InfoData payload, MessageCookie cookie)
            throws UnexpectedInputException, MessageDispatchException {
        if (payload instanceof SwitchEntities) {
            handleExpectedSwitchEntities((SwitchEntities) payload, cookie);
        } else {
            throw new UnexpectedInputException(payload);
        }
    }

    /**
     * Handle switch validate request.
     */
    public void handleSwitchValidateRequest(String key, SwitchValidateRequest request) {
        SwitchManagerCarrierCookieDecorator fsmCarrier = new SwitchManagerCarrierCookieDecorator(carrier, key);
        SwitchValidateFsm fsm =
                builder.newStateMachine(
                        SwitchValidateState.START, fsmCarrier, key, request, validationService, persistenceManager,
                        request.getValidationFilters());
        MeterRegistryHolder.getRegistry().ifPresent(registry -> {
            Sample sample = LongTaskTimer.builder("fsm.active_execution")
                    .register(registry)
                    .start();
            fsm.addTerminateListener(e -> {
                long duration = sample.stop();
                if (fsm.getCurrentState() == SwitchValidateState.FINISHED) {
                    registry.timer("fsm.execution.success")
                            .record(duration, TimeUnit.NANOSECONDS);
                } else if (fsm.getCurrentState() == SwitchValidateState.FINISHED_WITH_ERROR) {
                    registry.timer("fsm.execution.failed")
                            .record(duration, TimeUnit.NANOSECONDS);
                }
            });
        });
        handlers.put(key, fsm);

        SwitchValidateContext initialContext = SwitchValidateContext.builder().build();

        fsm.start();

        handle(fsm, SwitchValidateEvent.NEXT, initialContext);
    }

    private void handleFlowEntriesResponse(FlowDumpResponse data, MessageCookie cookie)
            throws MessageDispatchException {
        handle(cookie, SwitchValidateEvent.RULES_RECEIVED,
                SwitchValidateContext.builder().flowEntries(data.getFlowSpeakerData()).build());
    }

    private void handleGroupEntriesResponse(GroupDumpResponse data, MessageCookie cookie)
            throws MessageDispatchException {
        handle(cookie, SwitchValidateEvent.GROUPS_RECEIVED,
                SwitchValidateContext.builder().groupEntries(data.getGroupSpeakerData()).build());
    }

    private void handleLogicalPortResponse(DumpLogicalPortsResponse data, MessageCookie cookie)
            throws MessageDispatchException {
        handle(cookie, SwitchValidateEvent.LOGICAL_PORTS_RECEIVED, SwitchValidateContext.builder()
                .logicalPortEntries(data.getLogicalPorts()).build());
    }

    private void handleMeterEntriesResponse(MeterDumpResponse data, MessageCookie cookie)
            throws MessageDispatchException {
        handle(cookie, SwitchValidateEvent.METERS_RECEIVED,
                SwitchValidateContext.builder().meterEntries(data.getMeterSpeakerData()).build());
    }

    private void handleMetersUnsupportedResponse(MessageCookie key) throws MessageDispatchException {
        handle(key, SwitchValidateEvent.METERS_UNSUPPORTED, SwitchValidateContext.builder().build());
    }

    private void handleExpectedSwitchEntities(SwitchEntities expectedSwitchEntities, MessageCookie key)
            throws MessageDispatchException {
        handle(key, SwitchValidateEvent.EXPECTED_ENTITIES_BUILT, SwitchValidateContext.builder()
                .expectedEntities(expectedSwitchEntities.getEntities()).build());
    }

    private void handle(MessageCookie cookie, SwitchValidateEvent event, SwitchValidateContext context)
            throws MessageDispatchException {
        SwitchValidateFsm handler = null;
        if (cookie != null) {
            handler = handlers.get(cookie.getValue());
        }
        if (handler == null) {
            throw new MessageDispatchException(cookie);
        }
        context.setRequestCookie(cookie.getNested());
        handle(handler, event, context);
    }

    @VisibleForTesting
    void handle(SwitchValidateFsm fsm, SwitchValidateEvent event, SwitchValidateContext context) {
        fsmExecutor.fire(fsm, event, context);
        removeIfCompleted(fsm);
    }

    void removeIfCompleted(SwitchValidateFsm fsm) {
        if (fsm.isTerminated()) {
            log.info("Switch {} validation FSM have reached termination state (key={})",
                    fsm.getSwitchId(), fsm.getKey());
            handlers.remove(fsm.getKey());
            if (isAllOperationsCompleted() && !active) {
                carrier.sendInactive();
            }
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
        return handlers.isEmpty();
    }
}
