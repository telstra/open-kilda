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

package org.openkilda.wfm.topology.flowhs.service;

import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.service.SpeakerFlowSegmentRequestBuilder;

public class FlowCommandBuilderFactory {
    private final FlowResourcesManager resourcesManager;

    public FlowCommandBuilderFactory(FlowResourcesManager resourcesManager) {
        this.resourcesManager = resourcesManager;
    }

    /**
     * Provides a flow command factory depending on the encapsulation type.
     *
     * @param encapsulationType flow encapsulation type.
     * @return command factory.
     */
    public FlowCommandBuilder getBuilder(FlowEncapsulationType encapsulationType) {
        switch (encapsulationType) {
            case TRANSIT_VLAN:
            case VXLAN:
                return new SpeakerFlowSegmentRequestBuilder(resourcesManager);
            default:
                throw new UnsupportedOperationException(
                        String.format("Encapsulation type %s is not supported", encapsulationType));
        }
    }
}
