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

package org.openkilda.grpc.speaker;

import org.openkilda.grpc.speaker.client.GrpcSession;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class GrpcTestClient {

    private GrpcTestClient() {
    }

    public static void main(String[] args) throws Exception {
        String address = "test";
        String name = "test";
        GrpcSession sender = new GrpcSession(address);

        sender.login(name, name).get();

        log.warn(sender.dumpLogicalPorts().toString());
        log.warn(sender.showSwitchStatus().toString());

        Thread.sleep(1000000);
    }
}
