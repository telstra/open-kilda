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

package org.openkilda.floodlight.model;

import org.projectfloodlight.openflow.protocol.OFErrorMsg;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.DatapathId;

public class OfRequestResponse {
    private final DatapathId dpId;
    private final OFMessage request;
    private final long xid;
    private OFMessage response = null;

    public OfRequestResponse(DatapathId dpId, OFMessage request) {
        this.dpId = dpId;
        this.request = request;
        this.xid = request.getXid();
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

    /**
     * Is the response an error message.
     */
    public boolean isError() {
        boolean result;
        if (response != null) {
            result = response.getType() == OFType.ERROR;
        } else {
            result = false;
        }
        return result;
    }

    public void setResponse(OFMessage response) {
        this.response = response;
    }

    /**
     * Make string representation.
     */
    public String toString() {
        String result = String.format("%s ===> %s ===> ", formatOfMessage(request), dpId);
        if (response != null) {
            result += formatOfMessage(response);
        } else {
            result += "no response";
        }

        return result;
    }

    private static String formatOfMessage(OFMessage message) {
        String result = String.format("%s:%s xid: %d", message.getType(), message.getVersion(), message.getXid());
        if (message.getType() == OFType.ERROR) {
            OFErrorMsg errorMsg = (OFErrorMsg) message;
            result += String.format("error %s:%s", errorMsg.getErrType(), errorMsg);
        }
        return result;
    }
}
