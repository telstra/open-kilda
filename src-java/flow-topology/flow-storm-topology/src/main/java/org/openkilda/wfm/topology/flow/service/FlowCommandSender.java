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

package org.openkilda.wfm.topology.flow.service;

import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandGroup;

import java.util.List;

public interface FlowCommandSender {
    /**
     * Sends commands (install rule, remove rule, etc.) for the given flow.
     *
     * @param flowId            the flow to which the commands belongs to.
     * @param commandGroups     grouped commands to be sent for execution.
     * @param onSuccessCommands commands to be executed if commandGroups is successfully completed.
     * @param onFailureCommands commands to be executed if execution of commandGroups failed.
     */
    void sendFlowCommands(String flowId, List<CommandGroup> commandGroups,
                          List<? extends CommandData> onSuccessCommands,
                          List<? extends CommandData> onFailureCommands);
}
