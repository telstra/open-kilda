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

package org.openkilda.wfm.topology.network.controller.isl;

import org.openkilda.model.Isl;
import org.openkilda.model.IslDownReason;
import org.openkilda.model.IslStatus;
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.share.model.IslReference;
import org.openkilda.wfm.topology.network.controller.isl.IslFsm.IslFsmContext;
import org.openkilda.wfm.topology.network.controller.isl.IslFsm.IslFsmEvent;

import java.util.Optional;

public class DiscoveryRoundTripMonitor extends DiscoveryMonitor<IslStatus> {
    public DiscoveryRoundTripMonitor(IslReference reference) {
        super(reference);

        discoveryData.putBoth(IslStatus.INACTIVE);
        cache.putBoth(IslStatus.INACTIVE);
    }

    @Override
    public Optional<IslStatus> evaluateStatus() {
        boolean isActive = discoveryData.stream()
                .anyMatch(IslStatus.ACTIVE::equals);
        if (isActive) {
            return Optional.of(IslStatus.ACTIVE);
        }
        return Optional.empty();
    }

    @Override
    public IslDownReason getDownReason() {
        return null;  // this monitor can't produce down state
    }

    @Override
    public String getName() {
        return "round-trip";
    }

    @Override
    public void actualUpdate(IslFsmEvent event, IslFsmContext context) {
        if (event == IslFsmEvent.ROUND_TRIP_STATUS) {
            discoveryData.put(context.getEndpoint(), context.getRoundTripStatus().getStatus());
        }
    }

    @Override
    public void actualFlush(Endpoint endpoint, Isl persistentView) {
        persistentView.setRoundTripStatus(discoveryData.get(endpoint));
    }
}
