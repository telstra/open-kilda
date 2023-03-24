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

package org.openkilda.wfm.topology.switchmanager.fsm;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.openkilda.messaging.model.ValidationFilter.FLOW_INFO;
import static org.openkilda.messaging.model.ValidationFilter.GROUPS;
import static org.openkilda.messaging.model.ValidationFilter.LOGICAL_PORTS;
import static org.openkilda.messaging.model.ValidationFilter.METERS;
import static org.openkilda.messaging.model.ValidationFilter.METER_FLOW_INFO;
import static org.openkilda.messaging.model.ValidationFilter.RULES;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateState.START;

import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.switches.SwitchValidateRequest;
import org.openkilda.messaging.model.ValidationFilter;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateContext;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateEvent;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateState;
import org.openkilda.wfm.topology.switchmanager.service.SwitchManagerCarrier;
import org.openkilda.wfm.topology.switchmanager.service.ValidationService;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Optional;
import java.util.Set;

@RunWith(MockitoJUnitRunner.class)
public class SwitchValidateFsmTest {

    @Mock
    SwitchManagerCarrier carrier;

    @Mock
    ValidationService validationService;

    @Mock
    PersistenceManager persistenceManager;

    @Mock
    SwitchRepository switchRepository;

    @Mock
    RepositoryFactory factory;

    @Mock
    Switch sw;

    private FsmExecutor<SwitchValidateFsm, SwitchValidateState, SwitchValidateEvent, SwitchValidateContext> fsmExecutor;

    private static final String key = "KEY";
    private static final SwitchId SWITCH_ID = new SwitchId(1);
    private static final SwitchValidateContext EMPTY_CONTEXT = SwitchValidateContext.builder()
            .flowEntries(newArrayList())
            .groupEntries(newArrayList())
            .logicalPortEntries(emptyList())
            .meterEntries(emptyList())
            .expectedEntities(emptyList())
            .requestCookie(null)
            .build();

    @Before
    public void setUp() {
        this.fsmExecutor = new FsmExecutor<>(SwitchValidateFsm.SwitchValidateEvent.NEXT);

        when(persistenceManager.getRepositoryFactory()).thenReturn(factory);
        when(factory.createSwitchRepository()).thenReturn(switchRepository);
        when(switchRepository.findById(any())).thenReturn(Optional.ofNullable(sw));
    }

    @Test
    public void processV1Validation() {
        Set<ValidationFilter> filters = Sets.newHashSet(RULES, METERS, GROUPS, LOGICAL_PORTS, METER_FLOW_INFO);
        SwitchValidateFsm fsm = createFsmAndProceedToCollectData(filters);

        verify(carrier, times(3)).sendCommandToSpeaker(anyString(), any(CommandData.class));
        verify(carrier, times(1)).runHeavyOperation(key, SWITCH_ID);

        collectResourcesAndProceedToValidateState(fsm, filters);

        validateAndProceedToFinishedState(fsm, filters);
    }

    @Test
    public void processBasicV2Validation() {
        Set<ValidationFilter> filters = Sets.newHashSet(RULES, METERS, GROUPS, LOGICAL_PORTS, FLOW_INFO);

        SwitchValidateFsm fsm = createFsmAndProceedToCollectData(filters);

        verify(carrier, times(3)).sendCommandToSpeaker(anyString(), any(CommandData.class));
        verify(carrier, times(1)).runHeavyOperation(key, SWITCH_ID);

        collectResourcesAndProceedToValidateState(fsm, filters);

        validateAndProceedToFinishedState(fsm, filters);
    }

    @Test
    public void processSpecificV2Validation() {
        Set<ValidationFilter> filters = Sets.newHashSet(GROUPS, METERS, FLOW_INFO);
        SwitchValidateFsm fsm = createFsmAndProceedToCollectData(filters);

        verify(carrier, times(2)).sendCommandToSpeaker(anyString(), any(CommandData.class));
        verify(carrier, times(1)).runHeavyOperation(key, SWITCH_ID);

        collectResourcesAndProceedToValidateState(fsm, filters);

        validateAndProceedToFinishedState(fsm, filters);
    }

    @Test
    public void processSpecificV2ValidationWithExclude() {
        Set<ValidationFilter> filters = Sets.newHashSet(GROUPS, METERS);
        SwitchValidateFsm fsm = createFsmAndProceedToCollectData(filters);

        verify(carrier, times(2)).sendCommandToSpeaker(anyString(), any(CommandData.class));
        verify(carrier, times(1)).runHeavyOperation(key, SWITCH_ID);

        collectResourcesAndProceedToValidateState(fsm, filters);

        validateAndProceedToFinishedState(fsm, filters);
    }

    private SwitchValidateFsm createFsmAndProceedToCollectData(Set<ValidationFilter> filters) {
        SwitchValidateRequest request = SwitchValidateRequest.builder()
                .switchId(SWITCH_ID)
                .validationFilters(filters)
                .build();
        SwitchValidateFsm fsm = SwitchValidateFsm.builder().newStateMachine(
                START, carrier, key, request, validationService, persistenceManager, filters);

        fsmExecutor.fire(fsm, SwitchValidateEvent.NEXT);

        assertEquals(fsm.getCurrentState(), SwitchValidateState.COLLECT_DATA);
        return fsm;
    }

    private void collectResourcesAndProceedToValidateState(SwitchValidateFsm fsm, Set<ValidationFilter> filters) {
        fsm.expectedEntitiesBuilt(null, null, null, EMPTY_CONTEXT);
        if (filters.contains(RULES)) {
            fsm.rulesReceived(null, null, null, EMPTY_CONTEXT);
        }

        if (filters.contains(GROUPS)) {
            fsm.groupsReceived(null, null, null, EMPTY_CONTEXT);
        }

        if (filters.contains(LOGICAL_PORTS)) {
            fsm.logicalPortsReceived(null, null, null, EMPTY_CONTEXT);
        }

        if (filters.contains(METERS)) {
            fsm.metersReceived(null, null, null, EMPTY_CONTEXT);
        }

        assertEquals(fsm.getCurrentState(), SwitchValidateState.VALIDATE);
    }

    private void validateAndProceedToFinishedState(SwitchValidateFsm fsm, Set<ValidationFilter> filters) {
        if (filters.contains(GROUPS)) {
            verify(validationService).validateGroups(any(SwitchId.class), anyList(), anyList(), anyBoolean());
        }

        if (filters.contains(METERS)) {
            verify(validationService).validateMeters(
                    any(SwitchId.class), anyList(), anyList(), anyBoolean(), anyBoolean());
        }

        if (filters.contains(RULES)) {
            verify(validationService).validateRules(any(SwitchId.class), anyList(), anyList(), anyBoolean());
        }

        if (filters.contains(LOGICAL_PORTS)) {
            verify(validationService).validateLogicalPorts(any(SwitchId.class), anyList());
        }

        fsmExecutor.fire(fsm, SwitchValidateEvent.READY, null);

        assertEquals(fsm.getCurrentState(), SwitchValidateState.FINISHED);
    }
}
