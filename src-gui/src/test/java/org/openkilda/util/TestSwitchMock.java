/* Copyright 2024 Telstra Open Source
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

package org.openkilda.util;

public interface TestSwitchMock {

    String SWITCH_ID = "00:00:00:22:3d:5a:04:87";
    String SWITCH_ID_NULL = null;
    String PORT = "27";
    int SWITCH_PORT = 27;
    boolean MAINTENANCE_STATUS = false;
    boolean EVACUATE_STATUS = false;
}
