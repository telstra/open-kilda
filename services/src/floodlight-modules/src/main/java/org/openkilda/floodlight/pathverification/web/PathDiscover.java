/* Copyright 2017 Telstra Open Source
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

package org.openkilda.floodlight.pathverification.web;

import org.openkilda.floodlight.pathverification.IPathVerificationService;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PathDiscover extends ServerResource {
    protected static Logger logger = LoggerFactory.getLogger(PathDiscover.class);

    /**
     * Sends a discover packet.
     */
    @Put("json")
    public String sendDiscoverPacket() {
        IPathVerificationService pvs =
                (IPathVerificationService) getContext().getAttributes()
                        .get(IPathVerificationService.class.getCanonicalName());

        String srcSwitch = (String) getRequestAttributes().get("src_switch");
        String port = (String) getRequestAttributes().get("src_port");
        String dstSwitch = (String) getRequestAttributes().get("dst_switch");

        logger.debug("asking {} to send a discovery packet out port {} with destination {}.",
                srcSwitch, port, dstSwitch);

        if (dstSwitch == null) {
            DatapathId d = DatapathId.of(srcSwitch);
            int p = Integer.parseInt(port);
            pvs.sendDiscoveryMessage(d, OFPort.of(p));
        } else {
            pvs.sendDiscoveryMessage(DatapathId.of(srcSwitch), OFPort.of(new Integer(port)), DatapathId.of(dstSwitch));
        }
        return null;
    }
}
