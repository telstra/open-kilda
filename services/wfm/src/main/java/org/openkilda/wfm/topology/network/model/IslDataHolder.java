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

import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.model.Isl;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class IslDataHolder {
    private long speed;
    private int latency;
    private long maximumBandwidth;

    public IslDataHolder(IslInfoData speakerData) {
        speed = speakerData.getSpeed();
        latency = Isl.DEFAULT_LATENCY;
        maximumBandwidth = speakerData.getAvailableBandwidth();
    }

    public IslDataHolder(Isl entity) {
        speed = entity.getSpeed();
        latency = entity.getLatency();
        maximumBandwidth = entity.getMaxBandwidth();
    }

    private IslDataHolder(IslDataHolder first, IslDataHolder second) {
        speed = Math.min(first.speed, second.speed);
        latency = Math.max(first.latency, second.latency);
        maximumBandwidth = Math.min(first.maximumBandwidth, second.maximumBandwidth);
    }

    public IslDataHolder merge(IslDataHolder other) {
        return new IslDataHolder(this, other);
    }
}
