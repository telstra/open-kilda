/* Copyright 2020 Telstra Open Source
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

package org.openkilda.wfm.topology.floodlightrouter.service.monitor;

import org.openkilda.messaging.info.discovery.NetworkDumpSwitchData;
import org.openkilda.messaging.info.event.SwitchChangeType;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.floodlightrouter.service.SwitchMonitorCarrier;

import java.time.Clock;
import java.util.Objects;

public class SwitchReadOnlyConnectMonitor extends SwitchConnectMonitor {
    public SwitchReadOnlyConnectMonitor(SwitchMonitorCarrier carrier, Clock clock, SwitchId switchId) {
        super(carrier, clock, switchId);
    }

    @Override
    public void handleNetworkDumpResponse(NetworkDumpSwitchData switchData, String region) {
        if (! switchData.isWriteMode()) {
            super.handleNetworkDumpResponse(switchData, region);
        }
    }

    @Override
    protected void becomeUnavailableDueToRegionOffline() {
        // FIXME(surabujin): possible it must be ignored i.e. do nothing for read-only connections
        becomeUnavailable(new SwitchInfoData(switchId, SwitchChangeType.REMOVED));
    }

    @Override
    protected boolean isReadWriteMode() {
        return false;
    }

    @Override
    protected boolean isConnectNotification(SwitchInfoData notification) {
        return Objects.equals(SwitchChangeType.ADDED, notification.getState());
    }

    @Override
    protected boolean isDisconnectNotification(SwitchInfoData notification) {
        return Objects.equals(SwitchChangeType.REMOVED, notification.getState());
    }
}
