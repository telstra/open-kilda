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

package org.openkilda.wfm.share.flow.resources;

import org.openkilda.model.FlowMeter;
import org.openkilda.model.PathId;
import org.openkilda.model.TransitVlan;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class FlowResources {
    private PathId forwardPathId;
    private PathId reversePathId;
    private long unmaskedCookie;
    private FlowMeter forwardMeter;
    private FlowMeter reverseMeter;
    private TransitVlan forwardTransitVlan;
    private TransitVlan reverseTransitVlan;
}
