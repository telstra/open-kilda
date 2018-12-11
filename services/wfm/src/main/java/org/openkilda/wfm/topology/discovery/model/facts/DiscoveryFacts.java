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

package org.openkilda.wfm.topology.discovery.model.facts;

import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.model.Isl;
import org.openkilda.wfm.topology.discovery.model.IslReference;

import lombok.Value;

@Value
public class DiscoveryFacts {
    private IslReference reference;

    private Long speed;
    private Long latency;
    private Long availableBandwidth;

    public DiscoveryFacts(IslInfoData speakerData) {
        reference = IslReference.of(speakerData);
        speed = speakerData.getSpeed();
        latency = speakerData.getLatency();
        availableBandwidth = speakerData.getAvailableBandwidth();
    }

    public DiscoveryFacts(Isl payload) {
        reference = IslReference.of(payload);
        speed = payload.getSpeed();
        latency = (long) payload.getLatency();
        availableBandwidth = payload.getAvailableBandwidth();
    }

    public DiscoveryFacts(IslReference reference) {
        this.reference = reference;
        speed = availableBandwidth = null;
        latency = null;
    }
}
