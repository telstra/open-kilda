/* Copyright 2018 Telstra Open Source
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

package org.openkilda.messaging.info.flow;

import org.openkilda.messaging.StringSerializer;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.ImmutablePair;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.messaging.payload.flow.FlowState;

import com.google.common.collect.ImmutableList;
import org.junit.Assert;
import org.junit.Test;

public class FlowInfoDataTest implements StringSerializer {
    @Test
    public void serializeLoop() throws Exception {
        SwitchId dpIdAlpha = new SwitchId("ff:fe:00:00:00:00:00:01");
        SwitchId dpIdBeta = new SwitchId("ff:fe:00:00:00:00:00:02");
        SwitchId dpIdGamma = new SwitchId("ff:fe:00:00:00:00:00:03");

        ImmutableList<PathNode> forwardPath = ImmutableList.of(
                new PathNode(dpIdAlpha, 1, 1),
                new PathNode(dpIdGamma, 1, 3),
                new PathNode(dpIdGamma, 2, 4),
                new PathNode(dpIdBeta, 1, 5));
        ImmutableList<PathNode> reversePath = forwardPath.reverse();
        int i = 0;
        for (PathNode pathNode : reversePath) {
            pathNode.setSeqId(i++);
        }

        Flow forwardFlowThread = Flow.builder()
                .flowId("unit-test-flow0")
                .bandwidth(1000)
                .ignoreBandwidth(false)
                .periodicPings(false)
                .cookie(0x8001L)
                .lastUpdated("0")
                .sourceSwitch(dpIdAlpha).sourcePort(10).sourceVlan(100)
                .destinationSwitch(dpIdBeta).destinationPort(20).destinationVlan(200)
                .meterId(1)
                .transitVlan(1024)
                .state(FlowState.ALLOCATED)
                .flowPath(new PathInfoData(20, forwardPath))
                .build();
        Flow reverseFlowThread = forwardFlowThread.toBuilder()
                .sourceSwitch(forwardFlowThread.getDestinationSwitch())
                .sourcePort(forwardFlowThread.getDestinationPort())
                .sourcePort(forwardFlowThread.getDestinationVlan())
                .destinationSwitch(forwardFlowThread.getSourceSwitch())
                .destinationPort(forwardFlowThread.getSourcePort())
                .destinationVlan(forwardFlowThread.getSourceVlan())
                .flowPath(new PathInfoData(20, reversePath))
                .build();

        FlowInfoData origin = new FlowInfoData(
                forwardFlowThread.getFlowId(),
                new ImmutablePair<>(forwardFlowThread, reverseFlowThread),
                FlowOperation.CREATE, "unit-test-correlation-id");

        InfoMessage wrapper = new InfoMessage(origin, System.currentTimeMillis(), origin.getCorrelationId());
        serialize(wrapper);
        FlowInfoData decoded = (FlowInfoData) ((InfoMessage) deserialize()).getData();

        Assert.assertEquals(
                String.format("%s object has been mangled in serialisation/deserialization loop",
                        origin.getClass().getName()),
                origin, decoded);
    }
}
