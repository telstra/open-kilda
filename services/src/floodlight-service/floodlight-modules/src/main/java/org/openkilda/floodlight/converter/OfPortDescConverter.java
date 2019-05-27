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

import org.openkilda.messaging.info.switches.PortDescription;

import org.projectfloodlight.openflow.protocol.OFPortConfig;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFPortFeatures;
import org.projectfloodlight.openflow.protocol.OFPortState;

public final class OfPortDescConverter {

    /**
     * Convert {@link OFPortDesc} to PortDescription.
     * @param ofPortDesc port description to be converted.
     * @return result of transformation.
     */
    public static PortDescription toPortDescription(OFPortDesc ofPortDesc) {
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

    private OfPortDescConverter() {
        throw new UnsupportedOperationException();
    }
}
