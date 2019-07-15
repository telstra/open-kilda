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

package org.openkilda.floodlight.error;

import org.openkilda.floodlight.model.FlowSegmentMetadata;

import lombok.Getter;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.List;

@Getter
public class SwitchMissingFlowsException extends SwitchOperationException {
    private final FlowSegmentMetadata metadata;
    private final transient List<OFFlowMod> expectedMessages;
    private final transient List<OFFlowMod> missingMessages;

    public SwitchMissingFlowsException(
            DatapathId dpId, FlowSegmentMetadata metadata, List<OFFlowMod> expected, List<OFFlowMod> missing) {
        super(dpId, makeMessage(dpId, metadata, expected, missing));

        this.metadata = metadata;
        this.expectedMessages = expected;
        this.missingMessages = missing;
    }

    private static String makeMessage(
            DatapathId dpId, FlowSegmentMetadata metadata, List<OFFlowMod> expected, List<OFFlowMod> missing) {
        return String.format(
                "Detect %d missing OF flows on %s related to flow %s cookie %s (total verified %s OF messages)",
                missing.size(), dpId, metadata.getFlowId(), metadata.getCookie(), expected.size());
    }
}
