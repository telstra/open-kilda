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
import org.openkilda.model.FlowPath;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.model.MirrorContext;
import org.openkilda.wfm.share.model.SpeakerRequestBuildContext;
import org.openkilda.wfm.share.model.SpeakerRequestBuildContext.PathContext;

import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public interface FlowCommandBuilder {
    /**
     * Build install commands for ingress, transit(if needed) and egress rules for provided path only.
     */
    List<FlowSegmentRequestFactory> buildAll(CommandContext context, Flow flow, FlowPath path,
                                             SpeakerRequestBuildContext speakerRequestBuildContext);

    /**
     * Build install commands for ingress, transit(if needed) and egress rules for provided one direction path
     * and mirror context.
     */
    List<FlowSegmentRequestFactory> buildAll(CommandContext context, Flow flow, FlowPath path,
                                             SpeakerRequestBuildContext speakerRequestBuildContext,
                                             MirrorContext mirrorContext);

    /**
     * Build install commands for ingress, transit(if needed) and egress rules for provided paths.
     */
    List<FlowSegmentRequestFactory> buildAll(CommandContext context, Flow flow,
                                             FlowPath forwardPath, FlowPath reversePath,
                                             SpeakerRequestBuildContext speakerRequestBuildContext);

    /**
     * Build install commands for ingress, transit(if needed) and egress rules for provided paths and mirror context.
     */
    List<FlowSegmentRequestFactory> buildAll(CommandContext context, Flow flow,
                                             FlowPath forwardPath, FlowPath reversePath,
                                             SpeakerRequestBuildContext speakerRequestBuildContext,
                                             MirrorContext mirrorContext);

    /**
     * Build install commands for transit(if needed) and egress rules for active forward and reverse paths.
     */
    List<FlowSegmentRequestFactory> buildAllExceptIngress(CommandContext context, Flow flow);

    /**
     * Build install commands for transit(if needed) and egress rules for provided path only.
     */
    List<FlowSegmentRequestFactory> buildAllExceptIngress(CommandContext context, Flow flow, FlowPath path);

    /**
     * Build install commands for transit(if needed) and egress rules for provided one direction path
     * and mirror context.
     */
    List<FlowSegmentRequestFactory> buildAllExceptIngress(CommandContext context, Flow flow, FlowPath path,
                                                          MirrorContext mirrorContext);

    /**
     * Build install commands for transit(if needed) and egress rules for provided paths.
     */
    List<FlowSegmentRequestFactory> buildAllExceptIngress(
            CommandContext context, Flow flow, FlowPath path, FlowPath oppositePath);

    /**
     * Build install commands for transit(if needed) and egress rules for provided paths and mirror context.
     */
    List<FlowSegmentRequestFactory> buildAllExceptIngress(
            CommandContext context, Flow flow, FlowPath path, FlowPath oppositePath, MirrorContext mirrorContext);

    /**
     * Build commands for transit(if needed) and egress rules for provided paths severally (i.e. each path
     * segments returned severally).
     */
    Pair<FlowSegmentRequestFactoriesSequence, FlowSegmentRequestFactoriesSequence> buildAllExceptIngressSeverally(
            CommandContext context, Flow flow, FlowPath path, FlowPath oppositePath);

    /**
     * Build install commands for ingress rules for active forward and reverse paths.
     */
    List<FlowSegmentRequestFactory> buildIngressOnly(
            CommandContext context, Flow flow, SpeakerRequestBuildContext speakerRequestBuildContext);

    /**
     * Build install commands for ingress rules for provided paths.
     */
    List<FlowSegmentRequestFactory> buildIngressOnly(
            CommandContext context, Flow flow, FlowPath path, FlowPath oppositePath,
            SpeakerRequestBuildContext speakerRequestBuildContext);

    /**
     * Build install commands for ingress rules for provided paths and mirror context.
     */
    List<FlowSegmentRequestFactory> buildIngressOnly(
            CommandContext context, Flow flow, FlowPath path, FlowPath oppositePath,
            SpeakerRequestBuildContext speakerRequestBuildContext, MirrorContext mirrorContext);

    /**
     * Build commands for ingress segments for provided paths and return them severally.
     */
    Pair<FlowSegmentRequestFactoriesSequence, FlowSegmentRequestFactoriesSequence> buildIngressOnlySeverally(
            CommandContext context, Flow flow, FlowPath path, FlowPath oppositePath,
            SpeakerRequestBuildContext speakerRequestBuildContext);

    /**
     * Build install commands for ingress rules for provided paths.
     */
    List<FlowSegmentRequestFactory> buildIngressOnlyOneDirection(
            CommandContext context, Flow flow, FlowPath path, FlowPath oppositePath,
            PathContext pathContext);

    /**
     * Build install commands for ingress rules for provided paths and mirror context.
     */
    List<FlowSegmentRequestFactory> buildIngressOnlyOneDirection(
            CommandContext context, Flow flow, FlowPath path, FlowPath oppositePath,
            PathContext pathContext, MirrorContext mirrorContext);

    /**
     * Build install commands for egress rules for provided paths.
     */
    List<FlowSegmentRequestFactory> buildEgressOnly(
            CommandContext context, Flow flow, FlowPath path, FlowPath oppositePath);

    /**
     * Build install commands for egress rules for provided paths and mirror context.
     */
    List<FlowSegmentRequestFactory> buildEgressOnly(
            CommandContext context, Flow flow, FlowPath path, FlowPath oppositePath, MirrorContext mirrorContext);

    /**
     * Build install commands for egress rules for provided paths.
     */
    List<FlowSegmentRequestFactory> buildEgressOnlyOneDirection(
            CommandContext context, Flow flow, FlowPath path, FlowPath oppositePath);

    /**
     * Build install commands for egress rules for provided paths and mirror context.
     */
    List<FlowSegmentRequestFactory> buildEgressOnlyOneDirection(
            CommandContext context, Flow flow, FlowPath path, FlowPath oppositePath, MirrorContext mirrorContext);
}
