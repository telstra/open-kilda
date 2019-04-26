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

package org.openkilda.wfm.topology.network.controller.bfd;

import org.openkilda.messaging.model.NoviBfdSession;
import org.openkilda.wfm.topology.network.model.LinkStatus;
import org.openkilda.wfm.topology.network.service.IBfdPortCarrier;

import java.util.Optional;

public class BfdSessionRemoveAction extends Action {
    public BfdSessionRemoveAction(IBfdPortCarrier carrier, NoviBfdSession requestPayload, LinkStatus linkStatus) {
        super(linkStatus);
        speakerRequestKey = carrier.removeBfdSession(requestPayload);
    }

    @Override
    public String getLogIdentifier() {
        return "BFD session remove";
    }

    @Override
    protected Optional<ActionResult> evaluateResult() {
        if (haveSpeakerResponse) {
            ActionResult result = ActionResult.of(speakerResponse);
            if (linkStatus == LinkStatus.DOWN || !result.isSuccess(false)) {
                return Optional.of(result);
            }
        }
        return Optional.empty();
    }
}
