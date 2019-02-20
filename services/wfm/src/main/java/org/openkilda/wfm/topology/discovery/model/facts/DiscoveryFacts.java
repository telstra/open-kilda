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
import org.openkilda.wfm.topology.discovery.model.Endpoint;
import org.openkilda.wfm.topology.discovery.model.IslDataHolder;
import org.openkilda.wfm.topology.discovery.model.IslReference;

import lombok.Getter;

public class DiscoveryFacts {
    @Getter
    private final IslReference reference;
    private IslDataHolder[] islData = new IslDataHolder[2];

    public DiscoveryFacts(Endpoint endpoint, IslInfoData speakerData) {
        this(IslReference.of(speakerData), endpoint, new IslDataHolder(speakerData));
    }

    public DiscoveryFacts(Endpoint endpoint, Isl payload) {
        this(IslReference.of(payload), endpoint, new IslDataHolder(payload));
    }

    public DiscoveryFacts(IslReference reference) {
        this.reference = reference;
    }

    protected DiscoveryFacts(IslReference reference, Endpoint endpoint, IslDataHolder data) {
        this.reference = reference;

        int idx = dataIndexByEndpoint(reference, endpoint);
        islData[idx] = data;
    }

    public void renew(Endpoint endpoint, IslDataHolder islData) {
        int idx = dataIndexByEndpoint(reference, endpoint);
        this.islData[idx] = islData;
    }

    /**
     * Evaluate aggregated ISL data and fill corresponding ISL fields.
     */
    public void fillIslEntity(Isl entity) {
        IslDataHolder aggData = makeAggregatedData();
        if (aggData != null) {
            entity.setSpeed(aggData.getSpeed());
            entity.setLatency(aggData.getLatency());
            entity.setMaxBandwidth(aggData.getAvailableBandwidth());
            entity.setDefaultMaxBandwidth(aggData.getAvailableBandwidth());
        }
    }

    /**
     * Calculate available bandwidth taking into account aggregated isl data and provided used bandwidth.
     */
    public long getAvailableBandwidth(long usedBandwidth) {
        long available = 0;
        IslDataHolder aggData = makeAggregatedData();
        if (aggData != null) {
            available = aggData.getAvailableBandwidth() - usedBandwidth;
        }
        return Math.max(0, available);
    }

    private IslDataHolder makeAggregatedData() {
        IslDataHolder aggData = null;
        for (int idx = 0; idx < islData.length; idx++) {
            if (islData[idx] == null) {
                continue;
            }
            if (aggData == null) {
                aggData = (islData[idx]);
            } else {
                aggData = aggData.merge(islData[idx]);
            }
        }

        return aggData;
    }

    private static int dataIndexByEndpoint(IslReference ref, Endpoint e) {
        int idx;
        if (ref.getDest().equals(e)) {
            idx =  0; // forward event
        } else if (ref.getSource().equals(e)) {
            idx = 1; // reverse event
        } else {
            throw new IllegalArgumentException(String.format("Endpoint %s is not belong to ISL %s", e, ref));
        }
        return idx;
    }
}
