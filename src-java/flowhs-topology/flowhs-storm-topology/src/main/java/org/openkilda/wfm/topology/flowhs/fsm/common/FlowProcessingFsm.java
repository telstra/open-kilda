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

package org.openkilda.wfm.topology.flowhs.fsm.common;

import org.openkilda.wfm.CommandContext;

import lombok.Getter;

@Getter
public abstract class FlowProcessingFsm<T extends WithHistorySupportFsm<T, S, E, C>, S, E, C>
        extends WithHistorySupportFsm<T, S, E, C> {

    protected final String flowId;

    protected String errorReason;

    public FlowProcessingFsm(CommandContext commandContext, String flowId) {
        super(commandContext);
        this.flowId = flowId;
    }

    protected void fireError(E errorEvent, String errorReason) {
        if (this.errorReason != null) {
            log.error("Subsequent error fired: " + errorReason);
        } else {
            this.errorReason = errorReason;
        }

        fire(errorEvent);
    }
}
