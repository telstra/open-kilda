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
import static org.junit.Assert.assertEquals;

import org.openkilda.floodlight.api.request.rulemanager.FlowCommand;
import org.openkilda.floodlight.api.request.rulemanager.OfCommand;
import org.openkilda.messaging.command.switches.SwitchValidateRequest;
import org.openkilda.model.SwitchId;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.wfm.topology.switchmanager.service.configs.SwitchSyncConfig;

import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class SwitchSyncFsmTest {
    @Test
    public void cleanupDependenciesAndBuildCommandBatchesSmallBatchTest() {
        SwitchSyncFsm fsm = buildFsm(2);

        FlowSpeakerData command1 = buildFlowSpeakerCommandData();
        FlowSpeakerData command2 = buildFlowSpeakerCommandData();
        FlowSpeakerData command3 = buildFlowSpeakerCommandData(command1.getUuid(), command2.getUuid());

        List<List<OfCommand>> result = fsm.cleanupDependenciesAndBuildCommandBatches(
                newArrayList(command1, command2, command3));

        assertEquals(1, result.size());
        assertEquals(3, result.get(0).size());
        assertEquals(command3.getUuid(), ((FlowCommand) result.get(0).get(2)).getData().getUuid());
    }

    @Test
    public void cleanupDependenciesAndBuildCommandBatchesFullBatchTest() {
        SwitchSyncFsm fsm = buildFsm(5);

        FlowSpeakerData command1 = buildFlowSpeakerCommandData();
        FlowSpeakerData command2 = buildFlowSpeakerCommandData();
        FlowSpeakerData command3 = buildFlowSpeakerCommandData(command1.getUuid(), command2.getUuid());
        FlowSpeakerData command4 = buildFlowSpeakerCommandData();
        FlowSpeakerData command5 = buildFlowSpeakerCommandData();
        FlowSpeakerData command6 = buildFlowSpeakerCommandData();

        List<List<OfCommand>> result = fsm.cleanupDependenciesAndBuildCommandBatches(
                newArrayList(command1, command2, command3, command4, command5, command6));

        assertEquals(2, result.size());
        assertEquals(5, result.get(0).size());
        assertEquals(1, result.get(1).size());
    }

    @Test
    public void cleanupDependenciesAndBuildCommandBatchesMixedBatchTest() {
        SwitchSyncFsm fsm = buildFsm(3);

        FlowSpeakerData command1 = buildFlowSpeakerCommandData();
        FlowSpeakerData command2 = buildFlowSpeakerCommandData(command1.getUuid());
        FlowSpeakerData command3 = buildFlowSpeakerCommandData();
        FlowSpeakerData command4 = buildFlowSpeakerCommandData(command3.getUuid());
        FlowSpeakerData command5 = buildFlowSpeakerCommandData();
        FlowSpeakerData command6 = buildFlowSpeakerCommandData(command5.getUuid());
        FlowSpeakerData command7 = buildFlowSpeakerCommandData(command5.getUuid());
        FlowSpeakerData command8 = buildFlowSpeakerCommandData(command5.getUuid());

        List<List<OfCommand>> result = fsm.cleanupDependenciesAndBuildCommandBatches(
                newArrayList(command1, command2, command3, command4, command5, command6, command7, command8));

        assertEquals(3, result.size());
        assertEquals(2, result.get(0).size());
        assertEquals(2, result.get(1).size());
        assertEquals(4, result.get(2).size());
    }

    private SwitchSyncFsm buildFsm(int batchSize) {
        SwitchValidateRequest request = new SwitchValidateRequest(new SwitchId(1), true,
                true, true, Collections.emptySet(), Collections.emptySet());
        return new SwitchSyncFsm(null, null, null, request, null, new SwitchSyncConfig(batchSize));
    }

    private FlowSpeakerData buildFlowSpeakerCommandData(UUID... dependsOnUuid) {
        return FlowSpeakerData.builder()
                .uuid(UUID.randomUUID())
                .dependsOn(dependsOnUuid == null ? newArrayList() : newArrayList(dependsOnUuid))
                .build();
    }
}
