package org.bitbucket.openkilda.floodlight.pathverification.web;

import org.bitbucket.openkilda.floodlight.pathverification.IPathVerificationService;
import org.projectfloodlight.openflow.types.DatapathId;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class InstallMatch extends ServerResource {
protected static Logger logger = LoggerFactory.getLogger(PathDiscover.class);
  
  @Put("json")
  public String installMatch() {
    String srcSwitch = (String) getRequestAttributes().get("src_switch");
    Boolean isBroadcast = Boolean.parseBoolean((String) getRequestAttributes().get("is_broadcast"));
    
    IPathVerificationService pvs = 
        (IPathVerificationService) getContext().getAttributes().get(IPathVerificationService.class.getCanonicalName());
    pvs.installVerificationRule(DatapathId.of(srcSwitch), isBroadcast);
    return null;
  }
}
