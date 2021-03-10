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

package org.openkilda.util;

public interface TestFlowMock {

    static final String FLOW_ID_NULL = null;
    static final String FLOW_ID = "flow001";
    static final String TIME_FROM = "1582020869";
    static final String TIME_TO = "1582020869";
    //static final Long TIME_TO = System.currentTimeMillis();
    static final String TIME_LAST_SEEN = "2019-09-30T16:14:12.538Z";
    static final boolean CONTROLLER_FLAG = true;

}
