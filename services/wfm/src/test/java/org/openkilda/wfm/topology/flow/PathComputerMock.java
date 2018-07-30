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

import static java.util.Collections.emptyList;

import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.ImmutablePair;
import org.openkilda.pce.model.AvailableNetwork;
import org.openkilda.pce.provider.FlowInfo;
import org.openkilda.pce.provider.PathComputer;

import java.util.List;

public class PathComputerMock implements PathComputer {
    @Override
    public ImmutablePair<PathInfoData, PathInfoData> getPath(Flow flow, Strategy strategy) {
        return emptyPath();
    }

    @Override
    public ImmutablePair<PathInfoData, PathInfoData> getPath(Flow flow, AvailableNetwork network, Strategy strategy) {
        return emptyPath();
    }

    @Override
    public AvailableNetwork getAvailableNetwork(boolean ignoreBandwidth, int requestedBandwidth) {
        return new MockedAvailableNetwork();
    }

    private static ImmutablePair<PathInfoData, PathInfoData> emptyPath() {
        return new ImmutablePair<>(
                new PathInfoData(0L, emptyList()),
                new PathInfoData(0L, emptyList()));
    }

    @Override
    public long getWeight(IslInfoData isl) {
        return 1L;
    }

    @Override
    public List<FlowInfo> getFlowInfo() {
        return emptyList();
    }

    @Override
    public List<Flow> getAllFlows() {
        return emptyList();
    }

    @Override
    public List<Flow> getFlows(String flowId) {
        return emptyList();
    }

    @Override
    public List<SwitchInfoData> getSwitches() {
        return emptyList();
    }

    @Override
    public List<IslInfoData> getIsls() {
        return emptyList();
    }

    @Override
    public boolean isIslPort(String switchId, int port) {
        return false;
    }

    private class MockedAvailableNetwork extends AvailableNetwork {
        MockedAvailableNetwork() {
            super(null);
        }

        @Override
        public void addIslsOccupiedByFlow(String flowId) {
        }
    }
}
