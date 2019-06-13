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

package org.openkilda.wfm.error;

import org.openkilda.model.SwitchId;

public class IllegalIslStateException extends Exception {
    public IllegalIslStateException(SwitchId srcSwitch, Integer srcPort,
                                    SwitchId dstSwitch, Integer dstPort, String s) {
        super(String.format("Link with following parameters is in illegal state: "
                          + "source '%s_%d', destination '%s_%d'. %s",
                srcSwitch, srcPort, dstSwitch, dstPort, s));
    }

    public IllegalIslStateException(String message) {
        super(message);
    }
}
