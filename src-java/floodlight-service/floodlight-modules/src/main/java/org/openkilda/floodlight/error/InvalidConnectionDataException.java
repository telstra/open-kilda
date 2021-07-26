/* Copyright 2021 Telstra Open Source
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

import lombok.Getter;
import org.projectfloodlight.openflow.types.DatapathId;

import java.net.SocketAddress;

@Getter
public final class InvalidConnectionDataException extends SwitchOperationException {
    private final SocketAddress socketAddress;

    public static InvalidConnectionDataException ofSpeakerSocket(DatapathId dpId, SocketAddress socketAddress) {
        return new InvalidConnectionDataException(dpId, socketAddress, "speaker");
    }

    public static InvalidConnectionDataException ofSwitchSocket(DatapathId dpId, SocketAddress socketAddress) {
        return new InvalidConnectionDataException(dpId, socketAddress, "switch");
    }

    private InvalidConnectionDataException(DatapathId dpId, SocketAddress socketAddress, String kind) {
        super(dpId, String.format("Invalid %s ip socket address %s (dpid: %s)", kind, socketAddress, dpId));
        this.socketAddress = socketAddress;
    }
}
