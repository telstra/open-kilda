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

package org.openkilda.floodlight.switchmanager.web;

import net.floodlightcontroller.restserver.RestletRoutable;
import org.openkilda.floodlight.utils.RequestCorrelationFilter;
import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Filter;
import org.restlet.routing.Router;

/**
 * Created by jonv on 2/4/17.
 */
public class SwitchManagerWebRoutable implements RestletRoutable {
    @Override
    public Restlet getRestlet(Context context) {
        Router router = new Router(context);
        router.attach("/flow", FlowResource.class);
        router.attach("/flows/switch_id/{switch_id}", FlowsResource.class);
        router.attach("/meters/switch_id/{switch_id}", MetersResource.class);

        Filter filter = new RequestCorrelationFilter();
        filter.setNext(router);

        return router;
    }

    @Override
    public String basePath() {
        return "/wm/kilda";
    }
}
