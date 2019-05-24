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
import org.openkilda.wfm.topology.network.model.LinkStatus;
import org.openkilda.wfm.topology.network.service.IBfdPortCarrier;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.net.UnknownHostException;

@RunWith(MockitoJUnitRunner.class)
public class BfdSessionSetupActionTest extends AbstractBfdActionTest {
    @Mock
    private IBfdPortCarrier carrier;

    public BfdSessionSetupActionTest() throws UnknownHostException {
    }

    @Test
    public void happyPathResponseUp() {
        String requestKey = "BFD-session-setup-key";
        BfdAction action = makeAction(requestKey);

        // speaker response
        BfdSessionResponse response = new BfdSessionResponse(payload, null);
        Assert.assertFalse(action.consumeSpeakerResponse(requestKey, response).isPresent());

        // link become UP
        BfdAction.ActionResult result = action.updateLinkStatus(LinkStatus.UP)
                .orElseThrow(expectResultError);
        Assert.assertTrue(result.isSuccess());
    }

    @Test
    public void happyPathUpResponse() {
        String requestKey = "BFD-session-setup-key";
        BfdAction action = makeAction(requestKey);

        // link become UP
        Assert.assertFalse(action.updateLinkStatus(LinkStatus.UP).isPresent());

        // speaker response
        BfdSessionResponse response = new BfdSessionResponse(payload, null);
        BfdAction.ActionResult result = action.consumeSpeakerResponse(requestKey, response)
                .orElseThrow(expectResultError);
        Assert.assertTrue(result.isSuccess());
    }

    @Override
    protected BfdAction makeAction(String requestKey) {
        when(carrier.setupBfdSession(any(NoviBfdSession.class))).thenReturn(requestKey);

        final BfdAction action = new BfdSessionSetupAction(carrier, payload);

        verify(carrier).setupBfdSession(payload);
        verifyNoMoreInteractions(carrier);

        reset(carrier);

        return action;
    }
}
