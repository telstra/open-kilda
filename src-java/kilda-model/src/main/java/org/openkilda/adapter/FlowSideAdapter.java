/* Copyright 2019 Telstra Open Source
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

package org.openkilda.adapter;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.HaFlow;

public abstract class FlowSideAdapter {

    /**
     * Determine "forward" direction for provided flow/path pair and create adapter to access source endpoint.
     */
    public static FlowSideAdapter makeIngressAdapter(Flow flow, FlowPath path) {
        if (path.getCookie().getDirection() == FlowPathDirection.FORWARD) {
            return new FlowSourceAdapter(flow);
        } else {
            return new FlowDestAdapter(flow);
        }
    }

    /**
     * Determine "forward" direction for provided ha-flow/sub path pair and create adapter to access ingress endpoint.
     */
    public static FlowSideAdapter makeIngressAdapter(HaFlow haFlow, FlowPath subPath) {
        if (subPath.getCookie().getDirection() == FlowPathDirection.FORWARD) {
            return new HaFlowSharedAdapter(haFlow, subPath.getHaSubFlow());
        } else {
            return new HaFlowSubFlowAdapter(haFlow, subPath.getHaSubFlow());
        }
    }

    /**
     * Determine "forward" direction for provided flow/path pair and create adapter to access dest endpoint.
     */
    public static FlowSideAdapter makeEgressAdapter(Flow flow, FlowPath path) {
        if (path.getCookie().getDirection() == FlowPathDirection.FORWARD) {
            return new FlowDestAdapter(flow);
        } else {
            return new FlowSourceAdapter(flow);
        }
    }

    /**
     * Determine "forward" direction for provided ha-flow/sub path pair and create adapter to access egress endpoint.
     */
    public static FlowSideAdapter makeEgressAdapter(HaFlow haFlow, FlowPath subPath) {
        if (subPath.getCookie().getDirection() == FlowPathDirection.FORWARD) {
            return new HaFlowSubFlowAdapter(haFlow, subPath.getHaSubFlow());
        } else {
            return new HaFlowSharedAdapter(haFlow, subPath.getHaSubFlow());
        }
    }

    public abstract FlowEndpoint getEndpoint();

    public abstract boolean isDetectConnectedDevicesLldp();

    public abstract boolean isDetectConnectedDevicesArp();

    public abstract boolean isLooped();

    public abstract boolean isOneSwitchFlow();
}
