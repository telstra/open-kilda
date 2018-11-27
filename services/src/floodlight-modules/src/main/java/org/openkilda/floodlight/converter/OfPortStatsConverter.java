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

import static java.util.stream.Collectors.toList;

import org.openkilda.messaging.info.stats.PortStatsData;
import org.openkilda.messaging.info.stats.PortStatsEntry;
import org.openkilda.messaging.info.stats.PortStatsReply;
import org.openkilda.messaging.model.SwitchId;

import org.projectfloodlight.openflow.protocol.OFPortStatsEntry;
import org.projectfloodlight.openflow.protocol.OFPortStatsProp;
import org.projectfloodlight.openflow.protocol.OFPortStatsPropEthernet;
import org.projectfloodlight.openflow.protocol.OFPortStatsReply;
import org.projectfloodlight.openflow.protocol.OFVersion;

import java.util.List;

/**
 * Utility class that converts OFPortStats from the switch to kilda known format for further processing.
 */
public final class OfPortStatsConverter {

    private static final int ETHERNET_PROPERTY_TYPE = 0x0;

    /**
     * Convert list of {@link OFPortStatsReply} to {@link PortStatsData}.
     * @param data list of port stats replies to be converted.
     * @param switchId id of the switch from which these replies were gotten.
     * @return result of transformation {@link PortStatsData}.
     */
    public static PortStatsData toPostStatsData(List<OFPortStatsReply> data, SwitchId switchId) {
        List<PortStatsReply> replies = data.stream()
                .map(OfPortStatsConverter::toPortStatsReply)
                .collect(toList());
        return new PortStatsData(switchId, replies);
    }

    private static PortStatsReply toPortStatsReply(OFPortStatsReply reply) {
        List<PortStatsEntry> entries = reply.getEntries().stream()
                .map(OfPortStatsConverter::toPortStatsEntry)
                .collect(toList());
        return new PortStatsReply(reply.getXid(), entries);
    }

    private static PortStatsEntry toPortStatsEntry(OFPortStatsEntry entry) {
        long rxFrameErr = 0L;
        long rxOverErr = 0L;
        long rxCrcErr = 0L;
        long collisions = 0L;

        // Since version OF_14 bellow described get***() methods throw UnsupportedOperationException
        if (entry.getVersion().compareTo(OFVersion.OF_13) > 0) {
            for (OFPortStatsProp property : entry.getProperties()) {
                if (property.getType() == ETHERNET_PROPERTY_TYPE) {
                    OFPortStatsPropEthernet etherProps = (OFPortStatsPropEthernet) property;
                    rxFrameErr = etherProps.getRxFrameErr().getValue();
                    rxOverErr = etherProps.getRxOverErr().getValue();
                    rxCrcErr = etherProps.getRxCrcErr().getValue();
                    collisions = etherProps.getCollisions().getValue();
                    break;
                }
            }
        } else {
            rxFrameErr = entry.getRxFrameErr().getValue();
            rxOverErr = entry.getRxOverErr().getValue();
            rxCrcErr = entry.getRxCrcErr().getValue();
            collisions = entry.getCollisions().getValue();
        }

        return new PortStatsEntry(
                entry.getPortNo().getPortNumber(),
                entry.getRxPackets().getValue(),
                entry.getTxPackets().getValue(),
                entry.getRxBytes().getValue(),
                entry.getTxBytes().getValue(),
                entry.getRxDropped().getValue(),
                entry.getTxDropped().getValue(),
                entry.getRxErrors().getValue(),
                entry.getTxErrors().getValue(),
                rxFrameErr,
                rxOverErr,
                rxCrcErr,
                collisions);
    }

    private OfPortStatsConverter() {
    }
}
