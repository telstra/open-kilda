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

package org.openkilda.wfm.topology.flowhs.service.haflow.history;

import static org.openkilda.wfm.share.history.model.DumpType.STATE_AFTER;
import static org.openkilda.wfm.share.history.model.DumpType.STATE_BEFORE;

import org.openkilda.model.HaFlow;
import org.openkilda.wfm.share.history.model.HaFlowDumpData;
import org.openkilda.wfm.share.mappers.HaFlowHistoryMapper;

import lombok.Getter;

/**
 * This class comprises different values with fluent setters that is more convenient to use instead of
 * multiple arguments to a method.
 */
@Getter
public final class HaFlowHistory {
    private String haFlowId;
    private String action;
    private String description;
    private final String taskId;
    private HaFlowDumpData flowDumpData;

    private HaFlowHistory(String taskId) {
        this.taskId = taskId;
    }

    public static HaFlowHistory withTaskId(String taskId) {
        return new HaFlowHistory(taskId);
    }

    public HaFlowHistory withAction(String action) {
        this.action = action;
        return this;
    }

    public HaFlowHistory withDescription(String description) {
        this.description = description;
        return this;
    }

    public HaFlowHistory withHaFlowId(String haFlowId) {
        this.haFlowId = haFlowId;
        return this;
    }

    public HaFlowHistory withHaFlowDump(HaFlowDumpData flowDumpData) {
        this.flowDumpData = flowDumpData;
        return this;
    }

    /**
     * This method is a combination of {@link  #withHaFlowDump} and {@link #withHaFlowId}.
     * @param haFlow save state of this HA-flow to history
     * @return this object to chain other methods
     */
    public HaFlowHistory withHaFlowDumpBefore(HaFlow haFlow) {
        this.flowDumpData = HaFlowHistoryMapper.INSTANCE.toHaFlowDumpData(haFlow, taskId, STATE_BEFORE);
        this.haFlowId = haFlow.getHaFlowId();
        return this;
    }

    /**
     * This method is a combination of {@link  #withHaFlowDump} and {@link #withHaFlowId}.
     * @param haFlow save state of this HA-flow to history
     * @return this object to chain other methods
     */
    public HaFlowHistory withHaFlowDumpAfter(HaFlow haFlow) {
        this.flowDumpData = HaFlowHistoryMapper.INSTANCE.toHaFlowDumpData(haFlow, taskId, STATE_AFTER);
        this.haFlowId = haFlow.getHaFlowId();
        return this;
    }
}
