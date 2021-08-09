/* Copyright 2021 Telstra Open Source
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
import org.openkilda.wfm.topology.network.NetworkTopologyDashboardLogger;
import org.openkilda.wfm.topology.network.model.OnlineStatus;
import org.openkilda.wfm.topology.network.service.ISwitchCarrier;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class LogicalLagPort extends AbstractPort {

    LogicalLagPort(Endpoint logicalEndpoint) {
        super(logicalEndpoint);
    }

    @Override
    public void portAdd(ISwitchCarrier carrier) {
        // no operation required
    }

    @Override
    public void portDel(ISwitchCarrier carrier) {
        // no operation required
    }

    @Override
    public void updateOnlineStatus(ISwitchCarrier carrier, OnlineStatus onlineStatus) {
        // no operation required
    }

    @Override
    public void updatePortLinkMode(ISwitchCarrier carrier) {
        // no operation required
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String makeDashboardPortLabel(NetworkTopologyDashboardLogger dashboardLogger) {
        return dashboardLogger.makePortLabel(this);
    }
}
