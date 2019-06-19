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
public class IslOneWayLatency extends IslBaseLatency {
    private static final long serialVersionUID = 5043236275282286971L;

    @JsonProperty("dst_switch_id")
    private SwitchId dstSwitchId;

    @JsonProperty("dst_port_no")
    private int dstPortNo;

    @JsonProperty("src_switch_supports_copy_field")
    private boolean srcSwitchSupportsCopyField;

    @JsonProperty("dst_switch_supports_copy_field")
    private boolean dstSwitchSupportsCopyField;

    @JsonProperty("dst_switch_supports_groups")
    private boolean dstSwitchSupportsGroups;

    @JsonCreator
    public IslOneWayLatency(@JsonProperty("src_switch_id") SwitchId srcSwitchId,
                            @JsonProperty("src_port_no") int srcPortNo,
                            @JsonProperty("dst_switch_id") SwitchId dstSwitchId,
                            @JsonProperty("dst_port_no") int dstPortNo,
                            @JsonProperty("latency_ns") long latency,
                            @JsonProperty("packet_id") Long packetId,
                            @JsonProperty("src_switch_supports_copy_field") boolean srcSwitchSupportsCopyField,
                            @JsonProperty("dst_switch_supports_copy_field") boolean dstSwitchSupportsCopyField,
                            @JsonProperty("dst_switch_supports_groups") boolean dstSwitchSupportsGroups) {
        super(srcSwitchId, srcPortNo, latency, packetId);
        this.dstSwitchId = dstSwitchId;
        this.dstPortNo = dstPortNo;
        this.srcSwitchSupportsCopyField = srcSwitchSupportsCopyField;
        this.dstSwitchSupportsCopyField = dstSwitchSupportsCopyField;
        this.dstSwitchSupportsGroups = dstSwitchSupportsGroups;
    }
}
