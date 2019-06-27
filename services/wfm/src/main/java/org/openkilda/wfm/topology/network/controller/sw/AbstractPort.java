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

package org.openkilda.wfm.topology.network.controller.sw;

import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.topology.network.model.LinkStatus;
import org.openkilda.wfm.topology.network.service.ISwitchCarrier;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public abstract class AbstractPort implements Serializable {
    private final Endpoint endpoint;

    private LinkStatus linkStatus;

    public AbstractPort(Endpoint endpoint) {
        this(endpoint, null);
    }

    public abstract void portAdd(ISwitchCarrier carrier);

    public abstract void portDel(ISwitchCarrier carrier);

    public abstract void updateOnlineStatus(ISwitchCarrier carrier, boolean mode);

    public abstract void updatePortLinkMode(ISwitchCarrier carrier);

    public boolean isSameKind(AbstractPort other) {
        return getClass() == other.getClass();
    }

    public int getPortNumber() {
        return endpoint.getPortNumber();
    }

    @Override
    public String toString() {
        return String.format("%s:%s status:%s", getClass().getCanonicalName(), endpoint, linkStatus);
    }

    /**
     * Type of a port for dashboard log.
     *
     * @return the type of port.
     */
    public abstract String getLogIdentifier();
}
