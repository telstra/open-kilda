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

package org.openkilda.wfm.topology.switchmanager.service;

import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;

import java.util.List;
import java.util.Set;

public interface LagOperationService {
    int createLagPort(SwitchId switchId, List<Integer> physicalPortNumbers);

    void removeCreatedLagPort(SwitchId switchId, List<Integer> physicalPortNumbers);

    void validatePhysicalPorts(SwitchId switchId, List<Integer> physicalPortNumbers, Set<SwitchFeature> features);

    String getSwitchIpAddress(Switch sw);

    Switch getSwitch(SwitchId switchId);
}