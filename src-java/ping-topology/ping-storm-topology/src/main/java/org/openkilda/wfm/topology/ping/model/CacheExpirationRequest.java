/* Copyright 2023 Telstra Open Source
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

package org.openkilda.wfm.topology.ping.model;

import lombok.Data;

@Data
public class CacheExpirationRequest {
    private final String flowId;
    private final long forwardCookie;
    private final long reverseCookie;

    public CacheExpirationRequest(FlowWithTransitEncapsulation flow) {
        if (flow.getHaFlow() != null) {
            flowId = flow.getHaFlow().getHaFlowId();
            forwardCookie = flow.getHaFlow().getForwardPath().getCookie().getValue();
            reverseCookie = flow.getHaFlow().getReversePath().getCookie().getValue();
        } else {
            flowId = flow.getFlow().getFlowId();
            forwardCookie = flow.getFlow().getForwardPath().getCookie().getValue();
            reverseCookie = flow.getFlow().getReversePath().getCookie().getValue();
        }
    }
}
