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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.floodlight.response.BfdSessionResponse;
import org.openkilda.messaging.model.NoviBfdSession;
import org.openkilda.wfm.topology.network.controller.bfd.Action.ActionResult;
import org.openkilda.wfm.topology.network.model.LinkStatus;
import org.openkilda.wfm.topology.network.service.IBfdPortCarrier;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.net.UnknownHostException;

@RunWith(MockitoJUnitRunner.class)
public class BfdSessionRemoveActionTest extends AbstractActionTest {
    @Mock
    private IBfdPortCarrier carrier;

    public BfdSessionRemoveActionTest() throws UnknownHostException {
    }

    @Test
    public void happyPathResponseDown() {
        String requestKey = "request-key";
        Action action = makeAction(requestKey, LinkStatus.UP);

        Assert.assertFalse(action.consumeSpeakerResponse(requestKey, new BfdSessionResponse(payload, null))
                                   .isPresent());

        ActionResult result = action.updateLinkStatus(LinkStatus.DOWN)
                .orElseThrow(() -> new AssertionError("Result must be defined at this moment"));
        Assert.assertTrue(result.isSuccess());
    }

    @Test
    public void happyPathDownResponse() {
        String requestKey = "request-key";
        Action action = makeAction(requestKey, LinkStatus.UP);

        Assert.assertFalse(action.updateLinkStatus(LinkStatus.DOWN).isPresent());

        ActionResult result = action.consumeSpeakerResponse(requestKey, new BfdSessionResponse(payload, null))
                .orElseThrow(() -> new AssertionError("Result must be defined at this moment"));
        Assert.assertTrue(result.isSuccess());
    }

    @Test
    public void happyPathUpResponseDown() {
        String requestKey = "request-key";
        Action action = makeAction(requestKey, LinkStatus.DOWN);

        Assert.assertFalse(action.updateLinkStatus(LinkStatus.UP).isPresent());

        Assert.assertFalse(action.consumeSpeakerResponse(requestKey, new BfdSessionResponse(payload, null))
                                   .isPresent());

        ActionResult result = action.updateLinkStatus(LinkStatus.DOWN)
                .orElseThrow(() -> new AssertionError("Result must be defined at this moment"));
        Assert.assertTrue(result.isSuccess());
    }

    @Test
    public void happyWithNotFoundError() {
        String requestKey = "request-key";
        Action action = makeAction(requestKey, LinkStatus.DOWN);

        action.consumeSpeakerResponse(requestKey, new BfdSessionResponse(
                payload, NoviBfdSession.Errors.NOVI_BFD_DISCRIMINATOR_NOT_FOUND_ERROR))
                .orElseThrow(() -> new AssertionError("Result must be defined at this moment"));
    }

    @Override
    protected Action makeAction(String requestKey) {
        return makeAction(requestKey, LinkStatus.UP);
    }

    private Action makeAction(String requestKey, LinkStatus linkStatus) {
        when(carrier.removeBfdSession(any(NoviBfdSession.class))).thenReturn(requestKey);

        Action action = new BfdSessionRemoveAction(carrier, payload, linkStatus);

        verify(carrier).removeBfdSession(payload);
        verifyNoMoreInteractions(carrier);

        reset(carrier);

        return action;
    }
}
