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

package org.openkilda.wfm.topology.ping.model;

import org.openkilda.messaging.model.BidirectionalFlow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class FlowsHeap {
    private final HashSet<FlowRef> set = new HashSet<>();

    public void add(BidirectionalFlow flow) {
        set.add(new FlowRef(flow.getFlowId(), flow.getForward().getCookie()));
        set.add(new FlowRef(flow.getFlowId(), flow.getReverse().getCookie()));
    }

    /**
     * Calc and return difference between current and `other` heaps.
     */
    public List<FlowRef> extra(FlowsHeap other) {
        ArrayList<FlowRef> result = new ArrayList<>();
        for (FlowRef entry : set) {
            if (!other.set.contains(entry)) {
                result.add(entry);
            }
        }
        return result;
    }
}
