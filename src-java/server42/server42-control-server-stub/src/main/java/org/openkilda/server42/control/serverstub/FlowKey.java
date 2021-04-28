/* Copyright 2020 Telstra Open Source
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

package org.openkilda.server42.control.serverstub;

import org.openkilda.server42.control.messaging.flowrtt.FlowRttControl.Flow;

import lombok.Builder;
import lombok.Value;

/**
 * Key for hashmaps from flow_id and direction. Both ends of flow can be connected to the same server42 instance.
 * https://github.com/telstra/open-kilda/issues/3695
 */
@Value
@Builder
class FlowKey {
    String flowId;
    boolean direction;

    static FlowKey fromFlow(Flow flow) {
        return FlowKey.builder().flowId(flow.getFlowId()).direction(flow.getDirection()).build();
    }
}
