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

    private Constants() {
        throw new UnsupportedOperationException();
    }
}
