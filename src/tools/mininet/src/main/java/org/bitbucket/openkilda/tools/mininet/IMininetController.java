package org.bitbucket.openkilda.tools.mininet;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.protocol.OFVersion;

public interface IMininetController {
  public IMininetController setIP(IPv4Address ip);
  public IMininetController setPort(TransportPort port);
  public IMininetController setVersion(OFVersion version);
  public IMininetController build();
}
