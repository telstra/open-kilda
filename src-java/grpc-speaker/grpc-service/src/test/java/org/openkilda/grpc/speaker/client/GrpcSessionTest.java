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

package org.openkilda.grpc.speaker.client;

import org.openkilda.grpc.speaker.exception.GrpcRequestFailureException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
class GrpcSessionTest {

    @Test
    void makeChannelWithValidAddressShouldCreateChannel() {
        String address = "192.168.1.1";
        assertNotNull(GrpcSession.makeChannel(address));
    }

    @Test
    void makeChannelWithInvalidAddressShouldProduceException() {
        GrpcRequestFailureException exception = Assertions.assertThrows(
                GrpcRequestFailureException.class, () -> GrpcSession.makeChannel("a.b.c.d"));

        Assertions.assertEquals("IP address is not valid.", exception.getMessage());
    }
}
