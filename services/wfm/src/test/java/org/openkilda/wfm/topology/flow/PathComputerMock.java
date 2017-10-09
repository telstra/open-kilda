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

import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.ImmutablePair;
import org.openkilda.pce.provider.PathComputer;

import com.google.common.graph.MutableNetwork;

import java.util.Collections;

public class PathComputerMock implements PathComputer {
    @Override
    public ImmutablePair<PathInfoData, PathInfoData> getPath(Flow flow) {
        return emptyPath();
    }

    @Override
    public ImmutablePair<PathInfoData, PathInfoData> getPath(SwitchInfoData source, SwitchInfoData destination, int bandwidth) {
        return emptyPath();
    }

    @Override
    public PathComputer withNetwork(MutableNetwork<SwitchInfoData, IslInfoData> network) {
        return null;
    }

    @Override
    public void init() {

    }

    private static ImmutablePair<PathInfoData, PathInfoData> emptyPath() {
        return new ImmutablePair<>(
                new PathInfoData(0L, Collections.emptyList()),
                new PathInfoData(0L, Collections.emptyList()));
    }
}
