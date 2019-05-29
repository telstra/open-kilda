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

import org.openkilda.floodlight.flow.request.InstallIngressRule;
import org.openkilda.floodlight.flow.request.InstallTransitRule;
import org.openkilda.floodlight.flow.request.RemoveRule;
import org.openkilda.model.Flow;
import org.openkilda.wfm.CommandContext;

import java.util.List;

public interface FlowCommandFactory {
    /**
     * Build list of commands for transit(if needed) and egress rules installation.
     * @param context command context.
     * @param flow flow data.
     * @return list of non ingress rules.
     */
    List<InstallTransitRule> createInstallNonIngressRules(CommandContext context, Flow flow);

    /**
     * Build list of commands for ingress rules installation.
     * @param context command context.
     * @param flow flow data.
     * @return list of non ingress rules.
     */
    List<InstallIngressRule> createInstallIngressRules(CommandContext context, Flow flow);

    /**
     * Build list of commands for transit(if needed) and egress rules deletion.
     * @param context command context.
     * @param flow flow data.
     * @return list of non ingress rules.
     */
    List<RemoveRule> createRemoveNonIngressRules(CommandContext context, Flow flow);

    /**
     * Build list of commands for ingress rules deletion.
     * @param context command context.
     * @param flow flow data.
     * @return list of non ingress rules.
     */
    List<RemoveRule> createRemoveIngressRules(CommandContext context, Flow flow);
}
