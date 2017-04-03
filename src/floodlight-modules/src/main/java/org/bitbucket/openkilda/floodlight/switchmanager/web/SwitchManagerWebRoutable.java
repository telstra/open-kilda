package org.bitbucket.openkilda.floodlight.switchmanager.web;

import net.floodlightcontroller.restserver.RestletRoutable;
import org.bitbucket.openkilda.floodlight.switchmanager.SwitchManager;
import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

/**
 * Created by jonv on 2/4/17.
 */
public class SwitchManagerWebRoutable implements RestletRoutable {
    @Override
    public Restlet getRestlet(Context context) {
        Router router = new Router(context);
        router.attach("/flow", FlowResource.class);
        return router;
    }

    @Override
    public String basePath() {
        return "/wm/kilda";
    }
}
