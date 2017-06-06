package org.bitbucket.openkilda.floodlight.pathverification.web;

import org.bitbucket.openkilda.floodlight.pathverification.IPathVerificationService;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class PathDiscover extends ServerResource {
    protected static Logger logger = LoggerFactory.getLogger(PathDiscover.class);

    @Put("json")
    public String sendDiscoverPacket() {
        IPathVerificationService pvs =
                (IPathVerificationService) getContext().getAttributes().get(IPathVerificationService.class.getCanonicalName());

        String srcSwitch = (String) getRequestAttributes().get("src_switch");
        String port = (String) getRequestAttributes().get("src_port");
        String dstSwitch = (String) getRequestAttributes().get("dst_switch");

        logger.debug("asking {} to send a discovery packet out port {} with destination {}.", new Object[]{srcSwitch, port, dstSwitch});

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
