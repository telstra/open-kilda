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

package org.openkilda.wfm.topology.ping.bolt.producer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openkilda.persistence.ferma.repositories.FermaModelUtils.buildSegments;

import org.openkilda.model.FlowPath;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;

import org.junit.Test;

public class ProducerUtilsTest {

    private static final Switch SWITCH_1 = Switch.builder().switchId(new SwitchId(1)).build();
    private static final Switch SWITCH_2 = Switch.builder().switchId(new SwitchId(2)).build();
    private static final Switch SWITCH_3 = Switch.builder().switchId(new SwitchId(3)).build();

    private static final PathId PATH_ID = new PathId("path_id");

    @Test
    public void getFirstIslPortOk() {
        final int expectedResult = 0;
        FlowPath flowPath = buildPath(PATH_ID, SWITCH_1, SWITCH_2);
        flowPath.setSegments(buildSegments(PATH_ID, SWITCH_1, SWITCH_2, SWITCH_3));

        int result = ProducerUtils.getFirstIslPort(flowPath);

        assertEquals(expectedResult, result);
    }

    @Test (expected = IllegalArgumentException.class)
    public void getFirstIslPortEmptySegments() {
        FlowPath flowPath = buildPath(PATH_ID, SWITCH_1, SWITCH_2);

        ProducerUtils.getFirstIslPort(flowPath);
    }

    @Test (expected = IllegalStateException.class)
    public void getFirstIslPortNoIngressSegment() {
        FlowPath flowPath = buildPath(PATH_ID, SWITCH_1, SWITCH_2);
        flowPath.setSegments(buildSegments(PATH_ID, SWITCH_2, SWITCH_3, SWITCH_1));

        ProducerUtils.getFirstIslPort(flowPath);
    }

    private FlowPath buildPath(
            PathId pathId, Switch srcSwitch, Switch dstSwitch) {
        return FlowPath.builder()
                .pathId(pathId)
                .srcSwitch(srcSwitch)
                .destSwitch(dstSwitch)
                .build();
    }

}
