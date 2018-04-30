/*
 * Copyright 2017 Telstra Open Source
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openkilda.northbound.dto;

import org.junit.Assert;
import org.junit.Test;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowVerificationResponse;
import org.openkilda.northbound.dto.flows.VerificationOutput;
import org.openkilda.northbound.utils.Converter;

import java.io.IOException;

public class VerificationOutputTest {
    private static final String verificationResponseJson = "{\"clazz\":\"org.openkilda.messaging.info.InfoMessage\",\"destination\":\"NORTHBOUND\",\"payload\":{\"clazz\":\"org.openkilda.messaging.info.flow.FlowVerificationResponse\",\"flow_id\":\"positive-flow-verify-1525039569973\",\"forward\":{\"clazz\":\"org.openkilda.messaging.info.flow.UniFlowVerificationResponse\",\"ping_success\":true,\"request\":{\"clazz\":\"org.openkilda.messaging.command.flow.UniFlowVerificationRequest\",\"packet_id\":\"ef6c4e35-0057-4239-8248-c1ad46169aa9\",\"timeout\":4000,\"flow_id\":\"positive-flow-verify-1525039569973\",\"direction\":\"FORWARD\",\"source_switch\":\"de:ad:be:ef:00:00:00:01\",\"source_port\":4,\"dest_switch\":\"de:ad:be:ef:00:00:00:07\",\"vlan\":96,\"timestamp\":1525039599920},\"timestamp\":1525039599954,\"measures\":{\"network_latency\":6,\"sender_latency\":0,\"recipient_latency\":0}},\"reverse\":{\"clazz\":\"org.openkilda.messaging.info.flow.UniFlowVerificationResponse\",\"ping_success\":true,\"request\":{\"clazz\":\"org.openkilda.messaging.command.flow.UniFlowVerificationRequest\",\"packet_id\":\"59b1f418-977f-411e-8fb5-299021ac3ba2\",\"timeout\":4000,\"flow_id\":\"positive-flow-verify-1525039569973\",\"direction\":\"REVERSE\",\"source_switch\":\"de:ad:be:ef:00:00:00:07\",\"source_port\":4,\"dest_switch\":\"de:ad:be:ef:00:00:00:01\",\"vlan\":112,\"timestamp\":1525039599920},\"timestamp\":1525039599970,\"measures\":{\"network_latency\":9,\"sender_latency\":0,\"recipient_latency\":0}},\"timestamp\":1525039600014},\"timestamp\":1525039600014,\"correlation_id\":\"ed2499bc-9eee-49a7-a78a-7f2f78dc7f92 : 1525039599865\"}";

    @Test
    public void serialize() throws IOException {
        InfoMessage response = Utils.MAPPER.readValue(
                verificationResponseJson, InfoMessage.class);
        VerificationOutput output = Converter.buildVerificationOutput((FlowVerificationResponse) response.getData());

        String json = Utils.MAPPER.writeValueAsString(output);
        Assert.assertNotNull(json);
    }
}
