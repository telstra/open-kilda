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

package org.openkilda.wfm.topology.discovery.model.facts;

import org.openkilda.messaging.model.SpeakerSwitchPortView;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.discovery.model.Endpoint;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class PortFacts implements Serializable {
    private final Endpoint endpoint;

    private LinkStatus linkStatus;

    protected PortFacts(PortFacts that) {
        this(that.getEndpoint(), that.getLinkStatus());
    }

    public PortFacts(Endpoint endpoint) {
        this(endpoint, null);
    }

    public PortFacts(SwitchId switchId,  SpeakerSwitchPortView portView) {
        this(Endpoint.of(switchId, portView.getNumber()), mapLinkStatus(portView.getState()));
    }

    public int getPortNumber() {
        return endpoint.getPortNumber();
    }

    private static LinkStatus mapLinkStatus(SpeakerSwitchPortView.State adminStatus) {
        switch (adminStatus) {
            case UP:
                return LinkStatus.UP;
            case DOWN:
                return LinkStatus.DOWN;
            default:
                throw new IllegalArgumentException(String.format(
                        "Unsupported port's admin status value %s (%s)",
                        adminStatus, adminStatus.getClass().getName()));
        }
    }

    public enum LinkStatus {
        UP, DOWN
    }
}
