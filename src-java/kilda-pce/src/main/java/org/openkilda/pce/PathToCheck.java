/* Copyright 2019 Telstra Open Source
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

package org.openkilda.pce;

import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.Path.Segment;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.io.Serializable;
import java.util.List;

@Value
@Builder
public class PathToCheck implements Serializable {
    private static final long serialVersionUID = 1L;

    @NonNull
    SwitchId srcSwitchId;
    @NonNull
    SwitchId destSwitchId;
    long latency;
    long latencyTier2;
    long minAvailableBandwidth;
    @NonNull List<Segment> segments;
    String diverseGroupId;
    boolean isBackupPath;
    PathComputationStrategy strategy;
    FlowEncapsulationType encapsulationType;
}
