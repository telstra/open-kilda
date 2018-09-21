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

package org.openkilda.pce.provider;

import org.openkilda.messaging.model.FlowDto;

public class UnroutablePathException extends Exception {
    private final FlowDto flow;

    public UnroutablePathException(FlowDto flow) {
        super(String.format(
                "Can't make flow from %s to %s (bandwidth=%d%s)",
                flow.getSourceSwitch(), flow.getDestinationSwitch(), flow.getBandwidth(),
                flow.isIgnoreBandwidth() ? " ignored" : ""));

        this.flow = flow;
    }

    public FlowDto getFlow() {
        return flow;
    }
}
