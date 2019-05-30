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

package org.openkilda.floodlight.error;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.types.DatapathId;

public class SwitchWriteException extends SwitchOperationException {
    private final transient OFMessage ofMessage;

    public SwitchWriteException(DatapathId dpId, OFMessage message) {
        this(dpId, message, null);
    }

    public SwitchWriteException(DatapathId dpId, OFMessage message, Throwable cause) {
        super(dpId, String.format(
                "Unable't to write message %s.%s:%s into %s",
                message.getType(), message.getVersion(), message.getXid(), dpId), cause);
        this.ofMessage = message;
    }

    public OFMessage getOfMessage() {
        return ofMessage;
    }
}
