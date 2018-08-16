/* Copyright 2018 Telstra Open Source
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

package org.openkilda.floodlight.converter;

import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.event.SwitchInfoExtendedData;
import org.openkilda.messaging.info.event.SwitchState;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.model.SwitchId;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.LogicalOFMessageCategory;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Converter of floodlight switch representation {@link net.floodlightcontroller.core.IOFSwitch}.
 */
public final class IofSwitchConverter {

    /**
     * Transforms {@link IOFSwitch} to object that is used throughout kilda in all components.
     *
     * @param sw switch data.
     * @param eventType switch state.
     * @return converted switch.
     */
    public static SwitchInfoData buildSwitchInfoData(IOFSwitch sw, SwitchState eventType) {
        SwitchId switchId = new SwitchId(sw.getId().getLong());
        InetSocketAddress address = (InetSocketAddress) sw.getInetAddress();
        InetSocketAddress controller = (InetSocketAddress) sw.getConnectionByCategory(
                LogicalOFMessageCategory.MAIN).getRemoteInetAddress();

        return new SwitchInfoData(
                switchId,
                eventType,
                String.format("%s:%d",
                        address.getHostString(),
                        address.getPort()),
                address.getHostName(),
                String.format("%s %s %s",
                        sw.getSwitchDescription().getManufacturerDescription(),
                        sw.getOFFactory().getVersion().toString(),
                        sw.getSwitchDescription().getSoftwareDescription()),
                controller.getHostString());
    }

    /**
     * Transforms {@link IOFSwitch} to object that is used throughout kilda in all components.
     *
     * @param sw switch data.
     * @param eventType switch state.
     * @param flowStats installed flows.
     * @return converted switch.
     */
    public static SwitchInfoExtendedData buildSwitchInfoDataExtended(IOFSwitch sw, SwitchState eventType,
                                                                     OFFlowStatsReply flowStats) {
        SwitchInfoData switchInfoData = buildSwitchInfoData(sw, eventType);

        List<FlowEntry> flows = flowStats.getEntries().stream()
                .map(OFFlowStatsConverter::toFlowEntry)
                .collect(Collectors.toList());

        return new SwitchInfoExtendedData(switchInfoData, flows);
    }

    private IofSwitchConverter() {
    }
}
