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

package org.openkilda.messaging.info.grpc;

import org.openkilda.messaging.model.grpc.PacketInOutStatsDto;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;

public class ResponseSerialisationTest {

    private ObjectMapper mapper = new ObjectMapper();

    @Test
    public void getPacketInOutStatsResponseTest() throws JsonProcessingException {
        GetPacketInOutStatsResponse response = new GetPacketInOutStatsResponse(new SwitchId(1),
                new PacketInOutStatsDto(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, true, 11));

        String jsonString = mapper.writeValueAsString(response);
        GetPacketInOutStatsResponse objectFromJson = mapper.readValue(jsonString, GetPacketInOutStatsResponse.class);
        Assert.assertEquals(response, objectFromJson);
    }
}
