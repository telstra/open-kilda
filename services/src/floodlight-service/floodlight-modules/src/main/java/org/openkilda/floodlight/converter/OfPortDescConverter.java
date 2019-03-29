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

import org.openkilda.messaging.info.event.PortChangeType;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.switches.PortDescription;
import org.openkilda.model.SwitchId;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import org.projectfloodlight.openflow.protocol.OFPortConfig;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFPortFeatures;
import org.projectfloodlight.openflow.protocol.OFPortState;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

@Mapper
public abstract class OfPortDescConverter {
    public static final OfPortDescConverter INSTANCE = Mappers.getMapper(OfPortDescConverter.class);

    /**
     * Convert {@link OFPortDesc} to PortDescription.
     * @param ofPortDesc port description to be converted.
     * @return result of transformation.
     */
    public PortDescription toPortDescription(OFPortDesc ofPortDesc) {
        return PortDescription.builder()
                .portNumber(ofPortDesc.getPortNo().getPortNumber())
                .hardwareAddress(ofPortDesc.getHwAddr().toString())
                .name(ofPortDesc.getName())
                .config(ofPortDesc.getConfig().stream()
                        .map(OFPortConfig::name)
                        .toArray(String[]::new))
                .state(ofPortDesc.getState().stream()
                        .map(OFPortState::name)
                        .toArray(String[]::new))
                .currentFeatures(ofPortDesc.getCurr().stream()
                        .map(OFPortFeatures::name)
                        .toArray(String[]::new))
                .advertisedFeatures(ofPortDesc.getAdvertised().stream()
                        .map(OFPortFeatures::name)
                        .toArray(String[]::new))
                .supportedFeatures(ofPortDesc.getSupported().stream()
                        .map(OFPortFeatures::name)
                        .toArray(String[]::new))
                .peerFeatures(ofPortDesc.getPeer().stream()
                        .map(OFPortFeatures::name)
                        .toArray(String[]::new))
                .currentSpeed(ofPortDesc.getCurrSpeed())
                .maxSpeed(ofPortDesc.getMaxSpeed())
                .build();
    }

    /**
     * Assemble {@link PortInfoData} from {@link DatapathId} and {@link OFPortDesc}.
     */
    public PortInfoData toPortInfoData(DatapathId dpId, OFPortDesc portDesc,
                                       net.floodlightcontroller.core.PortChangeType type) {
        return new PortInfoData(
                new SwitchId(dpId.getLong()), portDesc.getPortNo().getPortNumber(), mapChangeType(type),
                isPortEnabled(portDesc));
    }

    /**
     * Check is port number belong to reserved ports range.
     */
    public boolean isReservedPort(OFPort port) {
        // Due to lack of unsigned numeric types in java... this check must work with "negative values"
        // (OFPort.MAX == 0xffffff00 ie -256)
        return OFPort.MAX.getPortNumber() <= port.getPortNumber() && port.getPortNumber() <= -1;
    }

    /**
     * Analyse port status and report it's "enabled" state.
     */
    public boolean isPortEnabled(OFPortDesc portDesc) {
        // steal validation logic from {@link net.floodlightcontroller.core.internal.OFSwitch.portEnabled}
        return (!portDesc.getState().contains(OFPortState.BLOCKED)
                && !portDesc.getState().contains(OFPortState.LINK_DOWN)
                && !portDesc.getState().contains(OFPortState.STP_BLOCK));
    }

    private PortChangeType mapChangeType(net.floodlightcontroller.core.PortChangeType type) {
        switch (type) {
            case ADD:
                return PortChangeType.ADD;
            case OTHER_UPDATE:
                return PortChangeType.OTHER_UPDATE;
            case DELETE:
                return PortChangeType.DELETE;
            case UP:
                return PortChangeType.UP;
            default:
                throw new IllegalArgumentException(
                        String.format("Can't map %s.%s into %s",
                                      net.floodlightcontroller.core.PortChangeType.class.getName(),
                                      type, PortChangeType.class.getName()));
        }
    }
}
