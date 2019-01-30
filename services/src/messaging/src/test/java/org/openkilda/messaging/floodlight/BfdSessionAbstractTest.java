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

package org.openkilda.messaging.floodlight;

import org.openkilda.messaging.JsonSerializeAbstractTest;
import org.openkilda.messaging.model.NoviBfdSession;
import org.openkilda.messaging.model.Switch;
import org.openkilda.model.SwitchId;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.net.Inet4Address;
import java.net.UnknownHostException;

public abstract class BfdSessionAbstractTest extends JsonSerializeAbstractTest {
    protected NoviBfdSession makeBfdSession() throws UnknownHostException {
        return new NoviBfdSession(
                new Switch(
                        new SwitchId("ff:fe:00:00:00:00:00:01"),
                        Inet4Address.getByName("127.0.2.1"),
                        ImmutableSet.of(Switch.Feature.BFD), ImmutableList.of()),
                new Switch(
                        new SwitchId("ff:fd:00:00:00:00:00:02"),
                        Inet4Address.getByName("127.0.2.2"),
                        ImmutableSet.of(Switch.Feature.BFD), ImmutableList.of()),
                5, 65001, 1, 1005, 500, (short) 3, true);
    }
}
