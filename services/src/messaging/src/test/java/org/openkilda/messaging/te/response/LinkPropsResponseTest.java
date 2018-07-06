/* Copyright 2018 Telstra Open Source
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

package org.openkilda.messaging.te.response;

import org.openkilda.messaging.StringSerializer;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.model.LinkProps;
import org.openkilda.messaging.model.LinkPropsTest;
import org.openkilda.messaging.te.request.LinkPropsDrop;
import org.openkilda.messaging.te.request.LinkPropsDropTest;
import org.openkilda.messaging.te.request.LinkPropsPut;
import org.openkilda.messaging.te.request.LinkPropsPutTest;
import org.openkilda.messaging.te.request.LinkPropsRequest;

import org.junit.Assert;
import org.junit.Test;

public class LinkPropsResponseTest implements StringSerializer {
    @Test
    public void serializeLoop() throws Exception {
        LinkPropsRequest[] requestsBatch = new LinkPropsRequest[] {
                makePutRequest(),
                makeDropRequest()};

        for (LinkPropsRequest request : requestsBatch) {
            LinkProps result = LinkPropsTest.makeSubject();
            LinkPropsResponse origin = new LinkPropsResponse(request, result, null);
            InfoMessage wrapper = new InfoMessage(origin, System.currentTimeMillis(), getClass().getCanonicalName());
            serialize(wrapper);

            InfoMessage reconstructedWrapper = (InfoMessage) deserialize();
            LinkPropsResponse reconstructed = (LinkPropsResponse) reconstructedWrapper.getData();
            Assert.assertEquals(origin, reconstructed);
        }
    }

    private LinkPropsPut makePutRequest() {
        return LinkPropsPutTest.makeRequest();
    }

    private LinkPropsDrop makeDropRequest() {
        return LinkPropsDropTest.makeRequest();
    }
}
