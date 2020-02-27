/* Copyright 2020 Telstra Open Source
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
import org.openkilda.model.FlowPath;

import lombok.Getter;

public abstract class FlowPathAdapter {
    @Getter
    protected final Flow flow;

    public static FlowPathAdapter makePrimaryAdapter(Flow flow) {
        return new FlowPathPrimaryAdapter(flow);
    }

    public static FlowPathAdapter makeProtectedAdapter(Flow flow) {
        return new FlowPathProtectedAdapter(flow);
    }

    FlowPathAdapter(Flow flow) {
        this.flow = flow;
    }

    public abstract FlowPath getForwardPath();

    public abstract FlowPathAdapter setForwardPath(FlowPath path);

    public abstract FlowPath getReversePath();

    public abstract FlowPathAdapter setReversePath(FlowPath path);
}
