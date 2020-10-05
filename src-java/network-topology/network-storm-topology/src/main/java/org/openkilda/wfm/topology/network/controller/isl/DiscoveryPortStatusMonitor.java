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
import org.openkilda.wfm.topology.network.model.LinkStatus;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class DiscoveryPortStatusMonitor extends DiscoveryMonitor<LinkStatus> {
    public DiscoveryPortStatusMonitor(IslReference reference) {
        super(reference);

        discoveryData.putBoth(LinkStatus.UP);
        cache.putBoth(LinkStatus.UP);
    }

    @Override
    public Optional<IslStatus> evaluateStatus() {
        boolean isDown = discoveryData.stream()
                .anyMatch(entry -> entry == LinkStatus.DOWN);
        if (isDown) {
            return Optional.of(IslStatus.INACTIVE);
        }
        return Optional.empty();
    }

    @Override
    public IslDownReason getDownReason() {
        return IslDownReason.PORT_DOWN;
    }

    @Override
    public void actualUpdate(IslFsmEvent event, IslFsmContext context) {
        switch (event) {
            case ISL_DOWN:
                if (context.getDownReason() == IslDownReason.PORT_DOWN) {
                    discoveryData.put(context.getEndpoint(), LinkStatus.DOWN);
                }
                break;
            case ISL_UP:
                discoveryData.putBoth(LinkStatus.UP);
                break;

            default:
                // nothing to do here
        }
    }

    @Override
    public void actualFlush(Endpoint endpoint, Isl persistentView) {
        if (evaluateStatus().orElse(IslStatus.ACTIVE) == IslStatus.INACTIVE) {
            log.info("Set ISL {} ===> {} unstable time due to physical port down",
                    Endpoint.of(persistentView.getSrcSwitchId(), persistentView.getSrcPort()),
                    Endpoint.of(persistentView.getDestSwitchId(), persistentView.getDestPort()));
            persistentView.setTimeUnstable(persistentView.getTimeModify());
        }
    }
}
