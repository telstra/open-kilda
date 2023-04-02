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

package org.openkilda.messaging.info.stats;

import org.openkilda.messaging.payload.flow.PathNodePayload;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

import java.util.List;
import java.util.Set;

/**
 * A base for path info messages.
 */
@Getter
@AllArgsConstructor
@ToString
public abstract class BaseFlowPathInfo extends StatsNotification {
    @NonNull String flowId;
    String yFlowId;
    SwitchId yPointSwitchId;
    @NonNull FlowSegmentCookie cookie;
    MeterId meterId;
    @NonNull List<PathNodePayload> pathNodes;
    Set<Integer> statVlans;
    boolean ingressMirror;
    boolean egressMirror;
}
