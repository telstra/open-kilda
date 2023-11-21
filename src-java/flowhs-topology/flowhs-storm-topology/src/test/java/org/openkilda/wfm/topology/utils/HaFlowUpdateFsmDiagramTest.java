/* Copyright 2023 Telstra Open Source
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

package org.openkilda.wfm.topology.utils;

import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.service.haflow.HaFlowGenericCarrier;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.squirrelframework.foundation.component.SquirrelProvider;
import org.squirrelframework.foundation.fsm.DotVisitor;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;

import java.util.Collection;
import java.util.Collections;

@ExtendWith(MockitoExtension.class)
public class HaFlowUpdateFsmDiagramTest {

    @Mock
    private HaFlowGenericCarrier carrier;

    @Mock
    private CommandContext commandContext;

    @Test
    @Disabled("For manual execution only. Comment out this line for generating a DOT file")
    void createHaFlowUpdateFsmDiagram() {
        DotVisitor visitor = SquirrelProvider.getInstance().newInstance(DotVisitor.class);
        getHaFlowUpdateFsmCopy().accept(visitor);
        visitor.convertDotFile("src/main/resources/HaFlowUpdateFsm");
    }

    private HaFlowUpdateFsm getHaFlowUpdateFsmCopy() {
        // This code is created by copy-pasting it from the actual FSM and then all perform methods have been removed.
        // This is stupid, but it works. We can use it until there is a better approach.
        // There are various tools to convert a DOT file into a picture. Search for DOT to PNG converter on the internet

        StateMachineBuilder<HaFlowUpdateFsm, HaFlowUpdateFsm.State, HaFlowUpdateFsm.Event, HaFlowUpdateContext>
                builder = StateMachineBuilderFactory.create(HaFlowUpdateFsm.class, HaFlowUpdateFsm.State.class,
                HaFlowUpdateFsm.Event.class, HaFlowUpdateContext.class, CommandContext.class,
                HaFlowGenericCarrier.class, String.class, Collection.class);

        builder.transition().from(HaFlowUpdateFsm.State.INITIALIZED).to(HaFlowUpdateFsm.State.FLOW_VALIDATED)
                .on(HaFlowUpdateFsm.Event.NEXT);
        builder.transition().from(HaFlowUpdateFsm.State.INITIALIZED).to(HaFlowUpdateFsm.State.FINISHED_WITH_ERROR)
                .on(HaFlowUpdateFsm.Event.TIMEOUT);

        builder.transition().from(HaFlowUpdateFsm.State.FLOW_VALIDATED).to(HaFlowUpdateFsm.State.FLOW_UPDATED)
                .on(HaFlowUpdateFsm.Event.NEXT);
        builder.transitions().from(HaFlowUpdateFsm.State.FLOW_VALIDATED)
                .toAmong(HaFlowUpdateFsm.State.REVERTING_FLOW_STATUS, HaFlowUpdateFsm.State.REVERTING_FLOW_STATUS)
                .onEach(HaFlowUpdateFsm.Event.TIMEOUT, HaFlowUpdateFsm.Event.ERROR);

        builder.transition().from(HaFlowUpdateFsm.State.FLOW_UPDATED)
                .to(HaFlowUpdateFsm.State.PRIMARY_RESOURCES_ALLOCATED).on(HaFlowUpdateFsm.Event.NEXT);
        builder.transitions().from(HaFlowUpdateFsm.State.FLOW_UPDATED)
                .toAmong(HaFlowUpdateFsm.State.REVERTING_FLOW, HaFlowUpdateFsm.State.REVERTING_FLOW)
                .onEach(HaFlowUpdateFsm.Event.TIMEOUT, HaFlowUpdateFsm.Event.ERROR);

        builder.transition().from(HaFlowUpdateFsm.State.PRIMARY_RESOURCES_ALLOCATED)
                .to(HaFlowUpdateFsm.State.PROTECTED_RESOURCES_ALLOCATED)
                .on(HaFlowUpdateFsm.Event.NEXT);
        builder.transitions().from(HaFlowUpdateFsm.State.PRIMARY_RESOURCES_ALLOCATED)
                .toAmong(HaFlowUpdateFsm.State.NEW_RULES_REVERTED, HaFlowUpdateFsm.State.NEW_RULES_REVERTED,
                        HaFlowUpdateFsm.State.REVERTING_ALLOCATED_RESOURCES)
                .onEach(HaFlowUpdateFsm.Event.TIMEOUT, HaFlowUpdateFsm.Event.ERROR,
                        HaFlowUpdateFsm.Event.NO_PATH_FOUND);

        builder.transition().from(HaFlowUpdateFsm.State.PROTECTED_RESOURCES_ALLOCATED)
                .to(HaFlowUpdateFsm.State.RESOURCE_ALLOCATION_COMPLETED)
                .on(HaFlowUpdateFsm.Event.NEXT);
        builder.transitions().from(HaFlowUpdateFsm.State.PROTECTED_RESOURCES_ALLOCATED)
                .toAmong(HaFlowUpdateFsm.State.NEW_RULES_REVERTED, HaFlowUpdateFsm.State.NEW_RULES_REVERTED,
                        HaFlowUpdateFsm.State.REVERTING_ALLOCATED_RESOURCES)
                .onEach(HaFlowUpdateFsm.Event.TIMEOUT, HaFlowUpdateFsm.Event.ERROR,
                        HaFlowUpdateFsm.Event.NO_PATH_FOUND);

        builder.transition().from(HaFlowUpdateFsm.State.RESOURCE_ALLOCATION_COMPLETED)
                .to(HaFlowUpdateFsm.State.BUILDING_RULES)
                .on(HaFlowUpdateFsm.Event.NEXT);
        builder.transitions().from(HaFlowUpdateFsm.State.RESOURCE_ALLOCATION_COMPLETED)
                .toAmong(HaFlowUpdateFsm.State.NEW_RULES_REVERTED, HaFlowUpdateFsm.State.NEW_RULES_REVERTED)
                .onEach(HaFlowUpdateFsm.Event.TIMEOUT, HaFlowUpdateFsm.Event.ERROR);

        builder.transition().from(HaFlowUpdateFsm.State.BUILDING_RULES)
                .to(HaFlowUpdateFsm.State.INSTALLING_NON_INGRESS_RULES)
                .on(HaFlowUpdateFsm.Event.NEXT);
        builder.transitions().from(HaFlowUpdateFsm.State.BUILDING_RULES)
                .toAmong(HaFlowUpdateFsm.State.NEW_RULES_REVERTED, HaFlowUpdateFsm.State.NEW_RULES_REVERTED)
                .onEach(HaFlowUpdateFsm.Event.TIMEOUT, HaFlowUpdateFsm.Event.ERROR);

        builder.internalTransition().within(HaFlowUpdateFsm.State.INSTALLING_NON_INGRESS_RULES)
                .on(HaFlowUpdateFsm.Event.RESPONSE_RECEIVED);
        builder.transition().from(HaFlowUpdateFsm.State.INSTALLING_NON_INGRESS_RULES)
                .to(HaFlowUpdateFsm.State.NON_INGRESS_RULES_INSTALLED)
                .on(HaFlowUpdateFsm.Event.RULES_INSTALLED);
        builder.transitions().from(HaFlowUpdateFsm.State.INSTALLING_NON_INGRESS_RULES)
                .toAmong(HaFlowUpdateFsm.State.PATHS_SWAP_REVERTED, HaFlowUpdateFsm.State.PATHS_SWAP_REVERTED)
                .onEach(HaFlowUpdateFsm.Event.TIMEOUT, HaFlowUpdateFsm.Event.ERROR);

        builder.transition().from(HaFlowUpdateFsm.State.NON_INGRESS_RULES_INSTALLED)
                .to(HaFlowUpdateFsm.State.PATHS_SWAPPED)
                .on(HaFlowUpdateFsm.Event.NEXT);
        builder.transitions().from(HaFlowUpdateFsm.State.NON_INGRESS_RULES_INSTALLED)
                .toAmong(HaFlowUpdateFsm.State.REVERTING_PATHS_SWAP, HaFlowUpdateFsm.State.REVERTING_PATHS_SWAP)
                .onEach(HaFlowUpdateFsm.Event.TIMEOUT, HaFlowUpdateFsm.Event.ERROR);

        builder.transition()
                .from(HaFlowUpdateFsm.State.PATHS_SWAPPED)
                .to(HaFlowUpdateFsm.State.NOTIFY_FLOW_STATS_ON_NEW_PATHS)
                .on(HaFlowUpdateFsm.Event.NEXT);
        builder.transitions().from(HaFlowUpdateFsm.State.PATHS_SWAPPED)
                .toAmong(HaFlowUpdateFsm.State.REVERTING_PATHS_SWAP, HaFlowUpdateFsm.State.REVERTING_PATHS_SWAP)
                .onEach(HaFlowUpdateFsm.Event.TIMEOUT, HaFlowUpdateFsm.Event.ERROR);

        builder.transition().from(HaFlowUpdateFsm.State.NOTIFY_FLOW_STATS_ON_NEW_PATHS)
                .to(HaFlowUpdateFsm.State.INSTALLING_INGRESS_RULES)
                .on(HaFlowUpdateFsm.Event.NEXT);

        builder.internalTransition().within(HaFlowUpdateFsm.State.INSTALLING_INGRESS_RULES)
                .on(HaFlowUpdateFsm.Event.RESPONSE_RECEIVED);
        builder.transition().from(HaFlowUpdateFsm.State.INSTALLING_INGRESS_RULES)
                .to(HaFlowUpdateFsm.State.INGRESS_RULES_INSTALLED)
                .on(HaFlowUpdateFsm.Event.RULES_INSTALLED);
        builder.transitions().from(HaFlowUpdateFsm.State.INSTALLING_INGRESS_RULES)
                .toAmong(HaFlowUpdateFsm.State.REVERTING_PATHS_SWAP, HaFlowUpdateFsm.State.REVERTING_PATHS_SWAP)
                .onEach(HaFlowUpdateFsm.Event.TIMEOUT, HaFlowUpdateFsm.Event.ERROR);

        builder.transition().from(HaFlowUpdateFsm.State.INGRESS_RULES_INSTALLED)
                .to(HaFlowUpdateFsm.State.NEW_PATHS_INSTALLATION_COMPLETED)
                .on(HaFlowUpdateFsm.Event.NEXT);
        builder.transitions().from(HaFlowUpdateFsm.State.INGRESS_RULES_INSTALLED)
                .toAmong(HaFlowUpdateFsm.State.REVERTING_PATHS_SWAP, HaFlowUpdateFsm.State.REVERTING_PATHS_SWAP)
                .onEach(HaFlowUpdateFsm.Event.TIMEOUT, HaFlowUpdateFsm.Event.ERROR);

        builder.transition().from(HaFlowUpdateFsm.State.NEW_PATHS_INSTALLATION_COMPLETED)
                .to(HaFlowUpdateFsm.State.REMOVING_OLD_RULES).on(HaFlowUpdateFsm.Event.NEXT);
        builder.transitions().from(HaFlowUpdateFsm.State.NEW_PATHS_INSTALLATION_COMPLETED)
                .toAmong(HaFlowUpdateFsm.State.REVERTING_PATHS_SWAP, HaFlowUpdateFsm.State.REVERTING_PATHS_SWAP)
                .onEach(HaFlowUpdateFsm.Event.TIMEOUT, HaFlowUpdateFsm.Event.ERROR);

        builder.internalTransition().within(HaFlowUpdateFsm.State.REMOVING_OLD_RULES)
                .on(HaFlowUpdateFsm.Event.RESPONSE_RECEIVED);
        builder.transition().from(HaFlowUpdateFsm.State.REMOVING_OLD_RULES).to(HaFlowUpdateFsm.State.OLD_RULES_REMOVED)
                .on(HaFlowUpdateFsm.Event.RULES_REMOVED);
        builder.transitions().from(HaFlowUpdateFsm.State.REMOVING_OLD_RULES)
                .toAmong(HaFlowUpdateFsm.State.OLD_RULES_REMOVED, HaFlowUpdateFsm.State.OLD_RULES_REMOVED)
                .onEach(HaFlowUpdateFsm.Event.TIMEOUT, HaFlowUpdateFsm.Event.ERROR);

        builder.transition().from(HaFlowUpdateFsm.State.OLD_RULES_REMOVED)
                .to(HaFlowUpdateFsm.State.NOTIFY_FLOW_STATS_ON_REMOVED_PATHS).on(HaFlowUpdateFsm.Event.NEXT);

        builder.transition().from(HaFlowUpdateFsm.State.NOTIFY_FLOW_STATS_ON_REMOVED_PATHS)
                .to(HaFlowUpdateFsm.State.OLD_PATHS_REMOVAL_COMPLETED)
                .on(HaFlowUpdateFsm.Event.NEXT);

        builder.transition().from(HaFlowUpdateFsm.State.OLD_PATHS_REMOVAL_COMPLETED)
                .to(HaFlowUpdateFsm.State.DEALLOCATING_OLD_RESOURCES)
                .on(HaFlowUpdateFsm.Event.NEXT);
        builder.transitions().from(HaFlowUpdateFsm.State.OLD_PATHS_REMOVAL_COMPLETED)
                .toAmong(HaFlowUpdateFsm.State.DEALLOCATING_OLD_RESOURCES,
                        HaFlowUpdateFsm.State.DEALLOCATING_OLD_RESOURCES)
                .onEach(HaFlowUpdateFsm.Event.TIMEOUT, HaFlowUpdateFsm.Event.ERROR);

        builder.transition().from(HaFlowUpdateFsm.State.DEALLOCATING_OLD_RESOURCES)
                .to(HaFlowUpdateFsm.State.OLD_RESOURCES_DEALLOCATED).on(HaFlowUpdateFsm.Event.NEXT);

        builder.transition().from(HaFlowUpdateFsm.State.OLD_RESOURCES_DEALLOCATED)
                .to(HaFlowUpdateFsm.State.UPDATING_FLOW_STATUS).on(HaFlowUpdateFsm.Event.NEXT);
        builder.transitions().from(HaFlowUpdateFsm.State.OLD_RESOURCES_DEALLOCATED)
                .toAmong(HaFlowUpdateFsm.State.UPDATING_FLOW_STATUS, HaFlowUpdateFsm.State.UPDATING_FLOW_STATUS)
                .onEach(HaFlowUpdateFsm.Event.TIMEOUT, HaFlowUpdateFsm.Event.ERROR);

        builder.transition().from(HaFlowUpdateFsm.State.UPDATING_FLOW_STATUS)
                .to(HaFlowUpdateFsm.State.FLOW_STATUS_UPDATED).on(HaFlowUpdateFsm.Event.NEXT);

        builder.transition().from(HaFlowUpdateFsm.State.FLOW_STATUS_UPDATED)
                .to(HaFlowUpdateFsm.State.NOTIFY_FLOW_MONITOR).on(HaFlowUpdateFsm.Event.NEXT);
        builder.transitions().from(HaFlowUpdateFsm.State.FLOW_STATUS_UPDATED)
                .toAmong(HaFlowUpdateFsm.State.NOTIFY_FLOW_MONITOR_WITH_ERROR,
                        HaFlowUpdateFsm.State.NOTIFY_FLOW_MONITOR_WITH_ERROR)
                .onEach(HaFlowUpdateFsm.Event.TIMEOUT, HaFlowUpdateFsm.Event.ERROR);

        builder.onEntry(HaFlowUpdateFsm.State.REVERTING_PATHS_SWAP);
        builder.transition().from(HaFlowUpdateFsm.State.REVERTING_PATHS_SWAP)
                .to(HaFlowUpdateFsm.State.PATHS_SWAP_REVERTED)
                .on(HaFlowUpdateFsm.Event.NEXT);

        builder.onEntry(HaFlowUpdateFsm.State.PATHS_SWAP_REVERTED);
        builder.transition().from(HaFlowUpdateFsm.State.PATHS_SWAP_REVERTED)
                .to(HaFlowUpdateFsm.State.REVERTING_NEW_RULES)
                .on(HaFlowUpdateFsm.Event.NEXT);

        builder.internalTransition().within(HaFlowUpdateFsm.State.REVERTING_NEW_RULES)
                .on(HaFlowUpdateFsm.Event.RESPONSE_RECEIVED);
        builder.transition().from(HaFlowUpdateFsm.State.REVERTING_NEW_RULES)
                .to(HaFlowUpdateFsm.State.NEW_RULES_REVERTED)
                .on(HaFlowUpdateFsm.Event.RULES_REVERTED);
        builder.transitions().from(HaFlowUpdateFsm.State.REVERTING_NEW_RULES)
                .toAmong(HaFlowUpdateFsm.State.NEW_RULES_REVERTED, HaFlowUpdateFsm.State.NEW_RULES_REVERTED)
                .onEach(HaFlowUpdateFsm.Event.TIMEOUT, HaFlowUpdateFsm.Event.ERROR);

        builder.transition().from(HaFlowUpdateFsm.State.NEW_RULES_REVERTED)
                .to(HaFlowUpdateFsm.State.REVERTING_ALLOCATED_RESOURCES)
                .on(HaFlowUpdateFsm.Event.NEXT);

        builder.onEntry(HaFlowUpdateFsm.State.REVERTING_ALLOCATED_RESOURCES);
        builder.transitions().from(HaFlowUpdateFsm.State.REVERTING_ALLOCATED_RESOURCES)
                .toAmong(HaFlowUpdateFsm.State.RESOURCES_ALLOCATION_REVERTED)
                .onEach(HaFlowUpdateFsm.Event.NEXT);
        builder.transition().from(HaFlowUpdateFsm.State.RESOURCES_ALLOCATION_REVERTED)
                .to(HaFlowUpdateFsm.State.REVERTING_FLOW).on(HaFlowUpdateFsm.Event.NEXT);
        builder.transition().from(HaFlowUpdateFsm.State.RESOURCES_ALLOCATION_REVERTED)
                .to(HaFlowUpdateFsm.State.REVERTING_FLOW)
                .on(HaFlowUpdateFsm.Event.ERROR);

        builder.onEntry(HaFlowUpdateFsm.State.REVERTING_FLOW);
        builder.transition().from(HaFlowUpdateFsm.State.REVERTING_FLOW)
                .to(HaFlowUpdateFsm.State.REVERTING_FLOW_STATUS)
                .on(HaFlowUpdateFsm.Event.NEXT);

        builder.onEntry(HaFlowUpdateFsm.State.REVERTING_FLOW_STATUS);
        builder.transition().from(HaFlowUpdateFsm.State.REVERTING_FLOW_STATUS)
                .to(HaFlowUpdateFsm.State.NOTIFY_FLOW_MONITOR_WITH_ERROR)
                .on(HaFlowUpdateFsm.Event.NEXT);

        builder.onEntry(HaFlowUpdateFsm.State.FINISHED_WITH_ERROR);

        builder.transition()
                .from(HaFlowUpdateFsm.State.NOTIFY_FLOW_MONITOR)
                .to(HaFlowUpdateFsm.State.FINISHED)
                .on(HaFlowUpdateFsm.Event.NEXT);
        builder.transition()
                .from(HaFlowUpdateFsm.State.NOTIFY_FLOW_MONITOR_WITH_ERROR)
                .to(HaFlowUpdateFsm.State.FINISHED_WITH_ERROR)
                .on(HaFlowUpdateFsm.Event.NEXT);

        builder.defineFinalState(HaFlowUpdateFsm.State.FINISHED);
        builder.defineFinalState(HaFlowUpdateFsm.State.FINISHED_WITH_ERROR);

        return builder.newStateMachine(HaFlowUpdateFsm.State.INITIALIZED, commandContext, carrier, "dummy",
                Collections.emptyList());
    }
}
