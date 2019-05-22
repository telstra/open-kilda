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

import org.openkilda.messaging.floodlight.response.BfdSessionResponse;
import org.openkilda.messaging.model.NoviBfdSession;
import org.openkilda.messaging.model.NoviBfdSession.Errors;
import org.openkilda.wfm.topology.network.model.LinkStatus;

import lombok.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

abstract class BfdAction {
    protected Logger log = LoggerFactory.getLogger(getClass());

    protected LinkStatus linkStatus;

    protected String speakerRequestKey = null;
    protected BfdSessionResponse speakerResponse = null;
    protected boolean haveSpeakerResponse = false;

    BfdAction(LinkStatus linkStatus) {
        this.linkStatus = linkStatus;
    }

    public Optional<ActionResult> updateLinkStatus(LinkStatus status) {
        linkStatus = status;
        return evaluateResult();
    }

    public final Optional<ActionResult> consumeSpeakerResponse(String requestKey, BfdSessionResponse response) {
        if (speakerRequestKey != null && speakerRequestKey.equals(requestKey)) {
            haveSpeakerResponse = true;
            speakerRequestKey = null;

            speakerResponse = response;
        }

        return evaluateResult();
    }

    public abstract String getLogIdentifier();

    protected abstract Optional<ActionResult> evaluateResult();

    @Value
    static class ActionResult {
        private boolean success;

        /**
         * Error core (null in case of timeout).
         */
        private NoviBfdSession.Errors errorCode;

        static ActionResult of(BfdSessionResponse response) {
            if (response == null) {
                return timeout();
            }
            if (response.getErrorCode() != null) {
                return error(response.getErrorCode());
            }
            return success();
        }

        static ActionResult success() {
            return new ActionResult(true, null);
        }

        static ActionResult error(NoviBfdSession.Errors errorCode) {
            return new ActionResult(false, errorCode);
        }

        static ActionResult timeout() {
            return new ActionResult(false, null);
        }

        public boolean isSuccess() {
            return isSuccess(true);
        }

        public boolean isSuccess(boolean allowMissing) {
            if (success) {
                return true;
            }

            if (allowMissing) {
                return Errors.NOVI_BFD_DISCRIMINATOR_NOT_FOUND_ERROR == errorCode;
            }

            return false;
        }
    }
}
