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

package org.openkilda.wfm.topology.switchmanager.utils;

import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.api.request.FlowSegmentRequestHandler;
import org.openkilda.floodlight.api.request.IngressFlowSegmentRequest;
import org.openkilda.model.MeterConfig;
import org.openkilda.model.MeterId;

import lombok.Getter;

import java.util.HashSet;
import java.util.Set;

public class FlowSegmentRequestMeterIdExtractor implements FlowSegmentRequestHandler {
    @Getter
    private final Set<MeterId> seenMeterId = new HashSet<>();

    @Override
    public void handleFlowSegmentRequest(IngressFlowSegmentRequest request) {
        MeterConfig meterConfig = request.getMeterConfig();
        if (meterConfig != null) {
            seenMeterId.add(meterConfig.getId());
        }
    }

    @Override
    public void handleFlowSegmentRequest(FlowSegmentRequest request) {
        // nothing to do here
    }

    public void handle(FlowSegmentRequest unclassified) {
        unclassified.handle(this);
    }
}
