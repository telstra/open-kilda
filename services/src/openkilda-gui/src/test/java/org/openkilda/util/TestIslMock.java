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

package org.openkilda.util;

public interface TestIslMock {

    static final int SRC_PORT = 1;
    
    static final String SRC_SWITCH = "00:00:00:00:00:00:00:01";

    static final int DST_PORT = 3;

    static final String DST_SWITCH = "00:00:00:00:00:00:00:02";

    static final boolean UNDER_MAINTENANE_FLAG = true;

    static final boolean EVACUATE_FLAG = false;

    static final Long  USER_ID = (long) 1;

}
