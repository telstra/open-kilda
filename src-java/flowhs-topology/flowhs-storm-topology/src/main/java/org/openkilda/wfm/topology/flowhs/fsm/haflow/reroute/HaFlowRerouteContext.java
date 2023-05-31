/* Copyright 2023 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute;

import org.openkilda.model.IslEndpoint;
import org.openkilda.wfm.topology.flowhs.fsm.common.context.SpeakerResponseContext;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.Set;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class HaFlowRerouteContext extends SpeakerResponseContext {
    private String haFlowId;
    private Set<IslEndpoint> affectedIsl;
    private boolean forceReroute;
    private boolean ignoreBandwidth;
    private boolean effectivelyDown;
    private String rerouteReason;
}
