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

package org.openkilda.model.history;

import static org.junit.Assert.assertEquals;

import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.model.history.HaFlowEventDump.HaFlowEventDumpCloner;
import org.openkilda.model.history.HaFlowEventDump.HaFlowEventDumpData;
import org.openkilda.model.history.HaFlowEventDump.HaFlowEventDumpDataImpl;

import org.junit.Test;

import java.time.Instant;

public class HaFlowEventDumpTest {
    @Test
    public void deepCopyConsistentWithEquals() {
        HaFlowEventDumpData source = HaFlowEventDumpDataImpl.builder()
                .dumpType(DumpType.STATE_AFTER)
                .taskId("task")
                .haFlowId("HA Flow ID")
                .affinityGroupId("group ID")
                .allocateProtectedPath(true)
                .description("some description")
                .diverseGroupId("some diverse group ID")
                .encapsulationType(FlowEncapsulationType.VXLAN)
                .flowTimeCreate(Instant.now())
                .flowTimeModify(Instant.now())
                .forwardPathId(new PathId("forward path ID"))
                .haSubFlows("subflow 1")
                .ignoreBandwidth(true)
                .maxLatency(1L)
                .maxLatencyTier2(2L)
                .maximumBandwidth(100L)
                .pathComputationStrategy(PathComputationStrategy.LATENCY)
                .paths("path 1")
                .periodicPings(true)
                .pinned(true)
                .priority(1)
                .protectedForwardPathId(new PathId("protected forward path ID"))
                .protectedReversePathId(new PathId("protected reverse path ID"))
                .reversePathId(new PathId("reverse path ID"))
                .sharedInnerVlan(10)
                .sharedOuterVlan(20)
                .sharedPort(30)
                .sharedSwitchId(new SwitchId("00:11"))
                .status(FlowStatus.UP)
                .strictBandwidth(true)
                .build();

        HaFlowEventDumpData target = HaFlowEventDumpCloner.INSTANCE.deepCopy(source);

        assertEquals(source, target);
    }
}
