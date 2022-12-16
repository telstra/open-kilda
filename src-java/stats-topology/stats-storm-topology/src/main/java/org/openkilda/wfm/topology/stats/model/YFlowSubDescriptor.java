/* Copyright 2021 Telstra Open Source
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

package org.openkilda.wfm.topology.stats.model;

import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;

@Value
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class YFlowSubDescriptor extends BaseFlowDescriptor {
    @NonNull String yFlowId;
    @NonNull String subFlowId;
    @NonNull SwitchId yPointSwitchId;

    public YFlowSubDescriptor(
            SwitchId switchId, MeasurePoint measurePoint, @NonNull String yFlowId, @NonNull String subFlowId,
            @NonNull SwitchId yPointSwitchId, @NonNull FlowSegmentCookie cookie, MeterId meterId) {
        super(switchId, measurePoint, cookie, meterId);
        this.yFlowId = yFlowId;
        this.subFlowId = subFlowId;
        this.yPointSwitchId = yPointSwitchId;
    }

    @Override
    public void handle(KildaEntryDescriptorHandler handler) {
        handler.handleStatsEntry(this);
    }
}
