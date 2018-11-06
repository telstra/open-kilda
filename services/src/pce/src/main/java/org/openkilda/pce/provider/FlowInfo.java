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

package org.openkilda.pce.provider;

import java.io.Serializable;

/**
 * Simple class to capture the key fields of interest from the flow relationships.
 * Currently it is leveraged as part of synchronizing the flow cache.
 */
public final class FlowInfo implements Serializable {
    private String flowId;
    private long cookie;
    private int transitVlanId;
    private int meterId;
    private String srcSwitchId;

    public FlowInfo() {}

    public FlowInfo(String flowId, long cookie, int transitVlanId, int meterId, String srcSwitchId) {
        this.flowId = flowId;
        this.cookie = cookie;
        this.transitVlanId = transitVlanId;
        this.meterId = meterId;
        this.srcSwitchId = srcSwitchId;
    }

    public String getFlowId() {
        return flowId;
    }

    public FlowInfo setFlowId(String flowId) {
        this.flowId = flowId;
        return this;
    }

    public long getCookie() {
        return cookie;
    }

    public FlowInfo setCookie(long cookie) {
        this.cookie = cookie;
        return this;
    }

    public int getTransitVlanId() {
        return transitVlanId;
    }

    public FlowInfo setTransitVlanId(int transitVlanId) {
        this.transitVlanId = transitVlanId;
        return this;
    }

    public int getMeterId() {
        return meterId;
    }

    public FlowInfo setMeterId(int meterId) {
        this.meterId = meterId;
        return this;
    }

    public String getSrcSwitchId() {
        return srcSwitchId;
    }

    public FlowInfo setSrcSwitchId(String srcSwitchId) {
        this.srcSwitchId = srcSwitchId;
        return this;
    }
}
