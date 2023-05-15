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

package org.openkilda.model.history;

import java.time.Instant;
import java.util.List;

/**
 * A generic interface for holding data of a flow event to save it in history. Event dump holds data of a specific flow.
 * Dumps could be created before and after an action that changes a flow state.
 * @param <T> a flow dump type.
 */
public interface GenericFlowEventData<T, U> {
    Instant getTimestamp();

    void setTimestamp(Instant timestamp);

    String getActor();

    void setActor(String actor);

    String getAction();

    void setAction(String action);

    String getTaskId();

    void setTaskId(String taskId);

    String getDetails();

    void setDetails(String details);

    List<U> getEventActions();

    List<T> getEventDumps();
}
