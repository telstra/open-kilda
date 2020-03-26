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

import org.openkilda.floodlight.api.request.factory.FlowSegmentRequestFactory;
import org.openkilda.model.Flow;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.model.FlowPathSpeakerView;

import java.util.List;

public interface FlowCommandBuilder {
    /**
     * Build flow segment request factories for all segments for provided paths.
     */
    List<FlowSegmentRequestFactory> buildAll(
            CommandContext context, Flow flow, FlowPathSpeakerView path, FlowPathSpeakerView oppositePath);

    /**
     * Build flow segment request factories for transit(if needed) and egress segments for provided paths.
     */
    List<FlowSegmentRequestFactory> buildAllExceptIngress(
            CommandContext context, Flow flow, FlowPathSpeakerView path, FlowPathSpeakerView oppositePath);

    /**
     * Build flow segment request factories for ingress segments for provided paths.
     */
    List<FlowSegmentRequestFactory> buildIngressOnly(
            CommandContext context, Flow flow, FlowPathSpeakerView path, FlowPathSpeakerView oppositePath);
}
