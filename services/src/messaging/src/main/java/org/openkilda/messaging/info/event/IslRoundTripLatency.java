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

package org.openkilda.messaging.info.event;

import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IslRoundTripLatency extends IslBaseLatency {
    private static final long serialVersionUID = 4184676909685019373L;

    @JsonCreator
    public IslRoundTripLatency(@JsonProperty("src_switch_id") SwitchId srcSwitchId,
                               @JsonProperty("src_port_no") int srcPortNo,
                               @JsonProperty("latency_ns") long latency,
                               @JsonProperty("packet_id") Long packetId) {
        super(srcSwitchId, srcPortNo, latency, packetId);
    }
}
