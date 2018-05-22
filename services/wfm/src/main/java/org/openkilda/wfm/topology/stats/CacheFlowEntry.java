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

package org.openkilda.wfm.topology.stats;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.io.Serializable;

@Value
@AllArgsConstructor
@Builder(toBuilder = true)
public class CacheFlowEntry implements Serializable {

    @NonNull
    private String flowId;

    private String ingressSwitch;
    private String egressSwitch;

    public CacheFlowEntry(String flowId) {
        this(flowId, null, null);
    }

    /**
     * Make "clone" of existing object, replace ingressSwitch or egressSwitch with new value. Switch that must be
     * replaced determined by point argument value.
     */
    public CacheFlowEntry replace(String sw, MeasurePoint point) {
        CacheFlowEntryBuilder replacement = toBuilder();
        switch (point) {
            case INGRESS:
                replacement.ingressSwitch(sw);
                break;
            case EGRESS:
                replacement.egressSwitch(sw);
                break;
            default:
                throw new IllegalArgumentException(String.format("Unsupported measurement point value %s", point));
        }
        return replacement.build();
    }
}
