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

package org.openkilda.wfm.topology.network.model;

import org.openkilda.wfm.topology.network.NetworkTopologyConfig;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

@Value
@Builder
@AllArgsConstructor
public class NetworkOptions implements Serializable {
    private Long discoveryInterval;

    private Long discoveryRoundTripStatusInterval;

    private Long discoveryPacketTtl;

    private Long discoveryTimeout;

    private Integer bfdLogicalPortOffset;

    private boolean bfdEnabled;

    private long delayWarmUp;

    private long delayCoolingDown;

    private long delayMin;

    private long dbRepeatMaxDurationSeconds;

    private boolean isRemoveExcessWhenSwitchSync;

    private int countSynchronizationAttempts;

    private long rulesSynchronizationAttempts;

    private long antiFlapStatsDumpingInterval;

    public NetworkOptions(NetworkTopologyConfig topologyConfig) {
        discoveryInterval = TimeUnit.SECONDS.toNanos(topologyConfig.getDiscoveryInterval());
        discoveryRoundTripStatusInterval = TimeUnit.SECONDS.toNanos(
                topologyConfig.getDiscoveryRoundTripStatusInterval());
        discoveryPacketTtl = TimeUnit.SECONDS.toNanos(topologyConfig.getDiscoveryPacketTtl());
        discoveryTimeout = TimeUnit.SECONDS.toNanos(topologyConfig.getDiscoveryTimeout());

        bfdLogicalPortOffset = topologyConfig.getBfdPortOffset();
        bfdEnabled = topologyConfig.isBfdEnabled();

        delayWarmUp = TimeUnit.SECONDS.toNanos(topologyConfig.getPortUpDownThrottlingDelaySecondsWarmUp());
        delayCoolingDown = TimeUnit.SECONDS.toNanos(topologyConfig.getPortUpDownThrottlingDelaySecondsCoolDown());
        delayMin = TimeUnit.SECONDS.toNanos(topologyConfig.getPortUpDownThrottlingDelaySecondsMin());

        NetworkTopologyConfig.DiscoveryConfig discoveryConfig = topologyConfig.getDiscoveryConfig();
        dbRepeatMaxDurationSeconds = discoveryConfig.getDbRepeatsTimeFrameSeconds();

        isRemoveExcessWhenSwitchSync = topologyConfig.isRemoveExcessWhenSwitchSync();

        countSynchronizationAttempts = topologyConfig.getCountSynchronizationAttempts();
        antiFlapStatsDumpingInterval = TimeUnit.SECONDS.toNanos(topologyConfig.getPortAntiFlapStatsDumpingInterval());
        rulesSynchronizationAttempts = topologyConfig.getRulesSynchronizationAttempts();
    }
}
