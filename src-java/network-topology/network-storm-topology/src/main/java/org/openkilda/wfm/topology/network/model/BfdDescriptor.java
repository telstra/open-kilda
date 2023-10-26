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

package org.openkilda.wfm.topology.network.model;

import org.openkilda.messaging.model.SwitchReference;
import org.openkilda.model.BfdSession;

import lombok.Builder;
import lombok.Value;

import java.io.Serializable;

@Value
@Builder(toBuilder = true)
public class BfdDescriptor implements Serializable {
    private SwitchReference local;
    private SwitchReference remote;

    /**
     * Fill DB record with data stored in this descriptor.
     */
    public void fill(BfdSession dbView) {
        dbView.setSwitchId(local.getDatapath());
        dbView.setIpAddress(local.getInetAddress().getHostAddress());

        dbView.setRemoteSwitchId(remote.getDatapath());
        dbView.setRemoteIpAddress(remote.getInetAddress().getHostAddress());
    }
}
