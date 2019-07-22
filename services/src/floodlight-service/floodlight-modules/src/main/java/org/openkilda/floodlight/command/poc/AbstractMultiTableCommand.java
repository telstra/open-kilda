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

package org.openkilda.floodlight.command.poc;

import org.openkilda.floodlight.FloodlightResponse;
import org.openkilda.floodlight.command.SpeakerCommand;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchId;

import net.floodlightcontroller.util.FlowModUtils;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.U64;

public abstract class AbstractMultiTableCommand extends SpeakerCommand {
    protected static final int PRIORITY_FLOW = FlowModUtils.PRIORITY_MED;
    protected static final int PRIORITY_ISL_EGRESS = FlowModUtils.PRIORITY_HIGH;
    protected static final int PRIORITY_REINJECT = FlowModUtils.PRIORITY_HIGH;

    protected static final int VLAN_BIT_SIZE = 12;
    protected static final MacAddress LLDP_ETH_DST = MacAddress.of(0x0180c2000000L);

    protected static final U64 METADATA_OUTER_VLAN_MASK = U64.of(0x000fff);
    protected static final U64 METADATA_INNER_VLAN_MASK = U64.of(0xfff000);
    protected static final U64 METADATA_DOUBLE_VLAN_MASK = METADATA_OUTER_VLAN_MASK.or(METADATA_INNER_VLAN_MASK);

    protected static final U64 METADATA_SEEN_MARK = U64.of(0x20_0000_0000L);
    protected static final U64 METADATA_REINJECT_MARK = U64.of(0x10_0000_0000L);

    public AbstractMultiTableCommand(SwitchId switchId, MessageContext messageContext) {
        super(switchId, messageContext);
    }

    @Override
    protected FloodlightResponse buildResponse() {
        return new FloodlightResponse(messageContext);
    }
}
