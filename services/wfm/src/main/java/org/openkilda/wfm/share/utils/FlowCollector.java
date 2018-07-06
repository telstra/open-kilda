/*
 * Copyright 2018 Telstra Open Source
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openkilda.wfm.share.utils;

import org.openkilda.messaging.model.BidirectionalFlow;
import org.openkilda.messaging.model.Flow;

import java.util.ArrayList;
import java.util.List;

public class FlowCollector {
    private String flowId;
    private Flow forward = null;
    private Flow reverse = null;

    public void add(Flow flow) {
        Flow current;

        boolean isForward = flow.isForward();
        if (isForward) {
            current = forward;
        } else {
            current = reverse;
        }

        if (current != null) {
            throw new IllegalArgumentException(
                    String.format("The flow(%s) for %s is already set",
                            flowId, isForward ? "FORWARD" : "REVERSE"));
        }

        if (isForward) {
            forward = flow;
        } else {
            reverse = flow;
        }

        flowId = flow.getFlowId();
    }

    public Flow anyDefined() {
        if (forward != null) {
            return forward;
        }
        if (reverse != null) {
            return reverse;
        }

        // FIXME(surabujin): make custom exception class
        throw new IllegalArgumentException("No one half-flow pieces defined");
    }

    public BidirectionalFlow make() {
        List<String> missing = new ArrayList<>(2);
        if (forward == null) {
            missing.add("FORWARD is missing");
        }
        if (reverse == null) {
            missing.add("REVERSE is missing");
        }
        if (0 < missing.size()) {
            throw new IllegalArgumentException(
                    String.format(
                            "Flow pair is incomplete: %s",
                            String.join(" and ", missing))
            );
        }

        return new BidirectionalFlow(forward, reverse);
    }
}
