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
import org.openkilda.messaging.model.SwitchReference;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.network.controller.bfd.Action.ActionResult;
import org.openkilda.wfm.topology.network.model.LinkStatus;

import org.junit.Assert;
import org.junit.Test;

import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.Optional;

public class ActionTest {
    private NoviBfdSession payload = NoviBfdSession.builder()
            .target(new SwitchReference(new SwitchId(1), Inet4Address.getByName("192.168.1.1")))
            .remote(new SwitchReference(new SwitchId(2), Inet4Address.getByName("192.168.1.2")))
            .physicalPortNumber(1)
            .logicalPortNumber(201)
            .discriminator(1001)
            .udpPortNumber(BfdPortFsm.BFD_UDP_PORT)
            .intervalMs(BfdPortFsm.bfdPollInterval)
            .multiplier(BfdPortFsm.bfdFailCycleLimit)
            .keepOverDisconnect(true)
            .build();

    public ActionTest() throws UnknownHostException {
    }

    @Test
    public void updateLinkStatus() {
        Action action = new ActionImpl("dummy", LinkStatus.DOWN);
        Assert.assertEquals(LinkStatus.DOWN, action.linkStatus);

        action.updateLinkStatus(LinkStatus.UP);
        Assert.assertEquals(LinkStatus.UP, action.linkStatus);

        action.updateLinkStatus(LinkStatus.DOWN);
        Assert.assertEquals(LinkStatus.DOWN, action.linkStatus);
    }

    @Test
    public void validResponse() {
        String requestKey = "request-key";
        Action action = new ActionImpl(requestKey, LinkStatus.DOWN);

        ActionResult result = action.consumeSpeakerResponse(requestKey, new BfdSessionResponse(payload, null))
                .orElseThrow(() -> new AssertionError("Action must produce result"));
        Assert.assertTrue(result.isSuccess());
    }

    @Test
    public void invalidResponse() {
        String requestKey = "request-key";
        Action action = new ActionImpl(requestKey, LinkStatus.DOWN);

        // invalid
        BfdSessionResponse response = new BfdSessionResponse(payload, null);
        Assert.assertFalse(action.consumeSpeakerResponse(requestKey + "+invalid", response).isPresent());

        // valid
        ActionResult result = action.consumeSpeakerResponse(requestKey, new BfdSessionResponse(payload, null))
                .orElseThrow(() -> new AssertionError("Action must produce result"));
        Assert.assertTrue(result.isSuccess());
    }

    @Test
    public void extraResult() {
        String requestKey = "request-key";
        Action action = new ActionImpl(requestKey, LinkStatus.DOWN);

        // actual result
        ActionResult result = action.consumeSpeakerResponse(requestKey, new BfdSessionResponse(payload, null))
                .orElseThrow(() -> new AssertionError("Action must produce result"));
        Assert.assertTrue(result.isSuccess());

        // extra invalid(timeout) result
        ActionResult result2 = action.consumeSpeakerResponse(requestKey, null)
                .orElseThrow(() -> new AssertionError("Action must produce result"));
        Assert.assertTrue(result.isSuccess());
    }

    @Test
    public void errorResponse() {
        ActionResult result = makeWithResponse(new BfdSessionResponse(payload, Errors.SWITCH_RESPONSE_ERROR));

        Assert.assertEquals(Errors.SWITCH_RESPONSE_ERROR, result.getErrorCode());
        Assert.assertFalse(result.isSuccess());
        Assert.assertFalse(result.isSuccess(true));
        Assert.assertFalse(result.isSuccess(false));
    }

    @Test
    public void timeoutResponse() {
        ActionResult result = makeWithResponse(null);

        Assert.assertNull(result.getErrorCode());
        Assert.assertFalse(result.isSuccess());
        Assert.assertFalse(result.isSuccess(true));
        Assert.assertFalse(result.isSuccess(false));
    }

    @Test
    public void missingSessionErrorResponse() {
        ActionResult result = makeWithResponse(
                new BfdSessionResponse(payload, Errors.NOVI_BFD_DISCRIMINATOR_NOT_FOUND_ERROR));

        Assert.assertTrue(result.isSuccess());
        Assert.assertTrue(result.isSuccess(true));
        Assert.assertFalse(result.isSuccess(false));
    }

    private ActionResult makeWithResponse(BfdSessionResponse response) {
        String requestKey = "request-key";
        Action action = new ActionImpl(requestKey, LinkStatus.DOWN);

        return action.consumeSpeakerResponse(requestKey, response)
                .orElseThrow(() -> new AssertionError("Action must produce result"));
    }

    private static class ActionImpl extends Action {
        ActionImpl(String requestKey, LinkStatus linkStatus) {
            super(linkStatus);
            this.speakerRequestKey = requestKey;
        }

        @Override
        public String getLogIdentifier() {
            return "test dummy";
        }

        @Override
        protected Optional<ActionResult> evaluateResult() {
            ActionResult result = null;
            if (haveSpeakerResponse) {
                result = ActionResult.of(speakerResponse);
            }
            return Optional.ofNullable(result);
        }
    }
}
