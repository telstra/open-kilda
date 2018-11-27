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

package org.openkilda.testing;

public final class Constants {
    public static final String ASWITCH_NAME = "aswitch";
    public static final String VIRTUAL_CONTROLLER_ADDRESS = "tcp:kilda:6653";
    public static final Integer DEFAULT_COST = 700;
    public static final Integer WAIT_OFFSET = 6;
    public static final Integer TOPOLOGY_DISCOVERING_TIME = 120;
    public static final Integer SWITCHES_ACTIVATION_TIME = 10;
    public static final Integer RULES_DELETION_TIME = 2;
    public static final Integer RULES_INSTALLATION_TIME = 2;

    private Constants() {
        throw new UnsupportedOperationException();
    }

    public enum DefaultRule {
        DROP_RULE(0x8000000000000001L),
        VERIFICATION_BROADCAST_RULE(0x8000000000000002L),
        VERIFICATION_UNICAST_RULE(0x8000000000000003L),
        DROP_LOOP_RULE(0x8000000000000004L);

        private final long cookie;

        DefaultRule(long cookie) {
            this.cookie = cookie;
        }

        public long getCookie() {
            return cookie;
        }
    }
}
