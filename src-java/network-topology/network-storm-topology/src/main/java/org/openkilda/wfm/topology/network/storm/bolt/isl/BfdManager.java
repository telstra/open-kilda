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

package org.openkilda.wfm.topology.network.storm.bolt.isl;

import org.openkilda.wfm.share.model.IslReference;
import org.openkilda.wfm.topology.network.service.IIslCarrier;

public class BfdManager {
    private final IslReference reference;

    public BfdManager(IslReference reference) {
        this.reference = reference;
    }

    public void enable(IIslCarrier carrier) {
        carrier.bfdEnableRequest(reference.getSource(), reference);
        carrier.bfdEnableRequest(reference.getDest(), reference);
    }

    public void disable(IIslCarrier carrier) {
        carrier.bfdDisableRequest(reference.getSource());
        carrier.bfdDisableRequest(reference.getDest());
    }

    /**
     * Send enable auxiliary poll mode request in case when BFD becomes active.
     */
    public void enableAuxiliaryPollMode(IIslCarrier carrier) {
        carrier.auxiliaryPollModeUpdateRequest(reference.getSource(), true);
        carrier.auxiliaryPollModeUpdateRequest(reference.getDest(), true);
    }

    /**
     * Send disable auxiliary poll mode request in case when BFD becomes inactive.
     */
    public void disableAuxiliaryPollMode(IIslCarrier carrier) {
        carrier.auxiliaryPollModeUpdateRequest(reference.getSource(), false);
        carrier.auxiliaryPollModeUpdateRequest(reference.getDest(), false);
    }
}
