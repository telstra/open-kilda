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

package org.openkilda.adapter;

import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaSubFlow;

public class HaFlowSharedAdapter extends FlowSideAdapter {
    private final HaFlow haFlow;
    private final HaSubFlow haSubFlow;

    public HaFlowSharedAdapter(HaFlow haFlow, HaSubFlow haSubFlow) {
        this.haFlow = haFlow;
        this.haSubFlow = haSubFlow;
    }

    @Override
    public FlowEndpoint getEndpoint() {
        return haFlow.getSharedEndpoint();
    }

    @Override
    public boolean isDetectConnectedDevicesLldp() {
        return false;
    }

    @Override
    public boolean isDetectConnectedDevicesArp() {
        return false;
    }

    @Override
    public boolean isLooped() {
        return false;
    }

    @Override
    public boolean isOneSwitchFlow() {
        return haFlow.getSharedSwitchId().equals(haSubFlow.getEndpointSwitchId());
    }
}
