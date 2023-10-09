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

package org.openkilda.wfm.topology.flowhs.service.history;


import static org.openkilda.wfm.share.history.model.DumpType.STATE_AFTER;
import static org.openkilda.wfm.share.history.model.DumpType.STATE_BEFORE;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.wfm.share.history.model.FlowDumpData;
import org.openkilda.wfm.share.mappers.HistoryMapper;

import lombok.Getter;

@Getter
public final class FlowHistory {
    private String flowId;
    private String action;
    private String description;
    private final String taskId;
    private FlowDumpData flowDumpData;

    private FlowHistory(String taskId) {
        this.taskId = taskId;
    }

    public static FlowHistory of(String taskId) {
        return new FlowHistory(taskId);
    }

    public FlowHistory withAction(String action) {
        this.action = action;
        return this;
    }

    public FlowHistory withDescription(String description) {
        this.description = description;
        return this;
    }

    public FlowHistory withFlowId(String flowId) {
        this.flowId = flowId;
        return this;
    }

    public FlowHistory withFlowDump(FlowDumpData flowDumpData) {
        this.flowDumpData = flowDumpData;
        return this;
    }

    /**
     * This method is a combination of {@link  #withFlowDump} and {@link #withFlowId}.
     * @param flow save state of this HA-flow to history
     * @return this object to chain other methods
     */
    public FlowHistory withFlowDumpBefore(Flow flow, FlowPath forward, FlowPath reverse) {
        this.flowDumpData = HistoryMapper.INSTANCE.map(flow, forward, reverse, STATE_BEFORE);
        this.flowId = flow.getFlowId();
        return this;
    }

    /**
     * This method is a combination of {@link  #withFlowDump} and {@link #withFlowId}.
     * @param flow save state of this HA-flow to history
     * @return this object to chain other methods
     */
    public FlowHistory withFlowDumpAfter(Flow flow, FlowPath forward, FlowPath reverse) {
        this.flowDumpData = HistoryMapper.INSTANCE.map(flow, forward, reverse, STATE_AFTER);
        this.flowId = flow.getFlowId();
        return this;
    }
}
