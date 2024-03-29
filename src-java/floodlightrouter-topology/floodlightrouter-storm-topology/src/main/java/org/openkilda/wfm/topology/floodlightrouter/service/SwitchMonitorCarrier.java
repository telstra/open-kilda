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

package org.openkilda.wfm.topology.floodlightrouter.service;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.model.SpeakerSwitchView;
import org.openkilda.messaging.model.SwitchAvailabilityData;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.floodlightrouter.model.RegionMappingUpdate;

public interface SwitchMonitorCarrier {
    void regionUpdateNotification(RegionMappingUpdate mappingUpdate);

    void sendSwitchConnectNotification(
            SwitchId switchId, SpeakerSwitchView speakerData, SwitchAvailabilityData availabilityData);

    void sendSwitchDisconnectNotification(
            SwitchId switchId, SwitchAvailabilityData availabilityData, boolean isRegionOffline);

    void sendSwitchAvailabilityUpdateNotification(SwitchId switchId, SwitchAvailabilityData availabilityData);

    void sendOtherNotification(SwitchId switchId, InfoData notification);
}
