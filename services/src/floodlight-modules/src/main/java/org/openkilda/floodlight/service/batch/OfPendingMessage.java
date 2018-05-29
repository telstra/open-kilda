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

package org.openkilda.floodlight.service.batch;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.types.DatapathId;

public class OfPendingMessage {
    private final DatapathId dpId;
    private final OFMessage request;
    private long xid;
    private OFMessage response = null;
    private boolean pending = true;

    public OfPendingMessage(DatapathId dpId, OFMessage request) {
        this.dpId = dpId;
        this.request = request;
        this.xid = request.getXid();
    }

    public boolean isPending() {
        return pending;
    }

    public long getXid() {
        return xid;
    }

    public DatapathId getDpId() {
        return dpId;
    }

    public OFMessage getRequest() {
        return request;
    }

    public OFMessage getResponse() {
        return response;
    }

    public void setResponse(OFMessage response) {
        pending = false;
        this.response = response;
    }
}
