/* Copyright 2017 Telstra Open Source
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

package org.openkilda.messaging.command.flow;

import static org.openkilda.messaging.Utils.MAPPER;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.flow.FlowInfoData;
import org.openkilda.messaging.info.flow.FlowOperation;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.ImmutablePair;

import org.junit.Test;

public class FlowInfoDataTest {
    @Test
    public void toStringTest() throws Exception {
        FlowInfoData data = new FlowInfoData("", new ImmutablePair<>(new Flow(), new Flow()),
                FlowOperation.CREATE, "time", Utils.DEFAULT_CORRELATION_ID);
        String dataString = data.toString();
        assertNotNull(dataString);
        assertFalse(dataString.isEmpty());

        System.out.println(MAPPER.writeValueAsString(data));
    }
}
