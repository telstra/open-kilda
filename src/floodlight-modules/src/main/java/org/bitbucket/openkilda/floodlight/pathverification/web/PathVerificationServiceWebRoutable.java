package org.bitbucket.openkilda.floodlight.pathverification.web;

import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

import net.floodlightcontroller.restserver.RestletRoutable;

public class PathVerificationServiceWebRoutable implements RestletRoutable {

  public String basePath() {
    return "/wm/pathverification";
  }

  public Restlet getRestlet(Context context) {
    Router router = new Router(context);
    router.attach("/discover/send_packet/{src_switch}/{src_port}", PathDiscover.class);
    router.attach("/discover/send_packet/{src_switch}/{src_port}/{dst_switch}", PathDiscover.class);
    return router;
  }
}
