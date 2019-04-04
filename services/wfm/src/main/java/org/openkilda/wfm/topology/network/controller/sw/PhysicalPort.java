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

import org.openkilda.model.Isl;
import org.openkilda.wfm.topology.network.model.Endpoint;
import org.openkilda.wfm.topology.network.service.ISwitchCarrier;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PhysicalPort extends AbstractPort {
    private Isl history;

    PhysicalPort(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void portAdd(ISwitchCarrier carrier) {
        carrier.setupPortHandler(getEndpoint(), history);
    }

    @Override
    public void portDel(ISwitchCarrier carrier) {
        carrier.removePortHandler(getEndpoint());
    }

    @Override
    public void updateOnlineStatus(ISwitchCarrier carrier, boolean mode) {
        carrier.setOnlineMode(getEndpoint(), mode);
    }

    @Override
    public void updatePortLinkMode(ISwitchCarrier carrier) {
        carrier.setPortLinkMode(getEndpoint(), getLinkStatus());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getLogIdentifier() {
        return "physical";
    }
}
