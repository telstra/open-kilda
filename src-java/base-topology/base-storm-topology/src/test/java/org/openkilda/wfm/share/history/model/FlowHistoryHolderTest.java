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

package org.openkilda.wfm.share.history.model;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.kafka.MessageDeserializer;
import org.openkilda.wfm.kafka.MessageSerializer;
import org.openkilda.wfm.share.history.model.HaFlowEventData.Event;
import org.openkilda.wfm.share.history.model.HaFlowEventData.Initiator;

import org.junit.Test;

import java.time.Instant;

public class FlowHistoryHolderTest {

    @Test
    public void serializationAndDeserialization() {

        FlowHistoryHolder historyHolder = FlowHistoryHolder.builder()
                .haFlowHistoryData(HaFlowHistoryData.builder()
                        .haFlowId("ha flow ID")
                        .time(Instant.now())
                        .action("test action")
                        .description("test description")
                        .build())
                .haFlowEventData(HaFlowEventData.builder()
                        .haFlowId("ha flow ID")
                        .event(Event.CREATE)
                        .details("test details")
                        .time(Instant.now())
                        .initiator(Initiator.AUTO)
                        .build())
                .haFlowDumpData(createHaFlowDumpData())
                .build();

        InfoMessage originalMessage = new InfoMessage(historyHolder, Instant.now().toEpochMilli(),
                "test correlation ID");

        try (MessageSerializer serializer = new MessageSerializer();
                MessageDeserializer deserializer = new MessageDeserializer()) {
            byte[] serialized = serializer.serialize("dummy", originalMessage);
            Message result = deserializer.deserialize("dummy", serialized);

            assertTrue(result instanceof InfoMessage);
            assertFalse(((InfoMessage) result).getErrorReport().isPresent());

            assertEquals(originalMessage.getData(), ((InfoMessage) result).getData());
        }
    }

    private HaFlowDumpData createHaFlowDumpData() {
        return HaFlowDumpData.builder()
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
                .ignoreBandwidth(true)
                .maxLatency(1L)
                .maxLatencyTier2(2L)
                .maximumBandwidth(100L)
                .pathComputationStrategy(PathComputationStrategy.LATENCY)
                .periodicPings(true)
                .pinned(true)
                .priority(1)
                .sharedInnerVlan(10)
                .sharedOuterVlan(20)
                .sharedPort(30)
                .sharedSwitchId(new SwitchId("00:11"))
                .status(FlowStatus.UP)
                .strictBandwidth(true)
                .build();
    }
}
