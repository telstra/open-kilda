/* Copyright 2017 Telstra Open Source
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

package org.openkilda.wfm.topology.flow;

import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.event.SwitchState;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.FlowPair;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.pce.model.AvailableNetwork;
import org.openkilda.pce.provider.PathComputer;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class PathComputerMock implements PathComputer {

    private static final SwitchId FAIL_SRC_SWITCH = new SwitchId("00:00:00:00:00:00:00:03");
    private static final SwitchId FAIL_DST_SWITCH = new SwitchId("00:00:00:00:00:00:00:04");

    @Override
    public FlowPair<PathInfoData, PathInfoData> getPath(Flow flow, Strategy strategy) {
        return emptyPath();
    }

    @Override
    public FlowPair<PathInfoData, PathInfoData> getPath(Flow flow, AvailableNetwork network, Strategy strategy) {
        return emptyPath();
    }

    @Override
    public AvailableNetwork getAvailableNetwork(boolean ignoreBandwidth, long requestedBandwidth) {
        return new MockedAvailableNetwork();
    }

    private static FlowPair<PathInfoData, PathInfoData> emptyPath() {
        return new FlowPair<>(
                new PathInfoData(0L, Collections.emptyList()),
                new PathInfoData(0L, Collections.emptyList()));
    }

    @Override
    public List<Flow> getFlow(String flowId) {
        return Collections.emptyList();
    }

    private class MockedAvailableNetwork extends AvailableNetwork {
        MockedAvailableNetwork() {
            super(null);
        }

        @Override
        public void addIslsOccupiedByFlow(String flowId, boolean ignoreBandwidth, long flowBandwidth) {

        }
    }

    @Override
    public Optional<SwitchInfoData> getSwitchById(SwitchId id) {
        if (Objects.equals(id, FAIL_SRC_SWITCH) || Objects.equals(id, FAIL_DST_SWITCH)) {
            return Optional.empty();
        }
        return Optional.of(new SwitchInfoData(id, SwitchState.ACTIVATED, "172.19.0.7:56484",
                "mininet.openkilda_default", "Nicira, Inc. OF_13 2.5.4",
                "mininet.openkilda_default"));
    }
}
