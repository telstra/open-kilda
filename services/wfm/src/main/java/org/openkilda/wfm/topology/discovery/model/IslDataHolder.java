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

package org.openkilda.wfm.topology.discovery.model;

import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.model.Isl;

import lombok.Value;

@Value
public class IslDataHolder {
    private long speed;
    private int latency;
    private long availableBandwidth;

    public IslDataHolder(IslInfoData speakerData) {
        speed = speakerData.getSpeed();
        latency = (int) speakerData.getLatency();
        availableBandwidth = speakerData.getAvailableBandwidth();
    }

    public IslDataHolder(Isl entity) {
        speed = entity.getSpeed();
        latency = entity.getLatency();
        availableBandwidth = entity.getAvailableBandwidth();
    }

    private IslDataHolder(IslDataHolder first, IslDataHolder second) {
        speed = Math.min(first.speed, second.speed);
        latency = Math.max(first.latency, second.latency);
        availableBandwidth = Math.min(first.latency, second.availableBandwidth);
    }

    public IslDataHolder merge(IslDataHolder other) {
        return new IslDataHolder(this, other);
    }
}
