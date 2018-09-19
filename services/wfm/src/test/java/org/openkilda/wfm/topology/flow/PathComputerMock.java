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
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.FlowPair;
import org.openkilda.pce.model.AvailableNetwork;
import org.openkilda.pce.provider.PathComputer;

import java.util.Collections;
import java.util.List;

public class PathComputerMock implements PathComputer {
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
}
