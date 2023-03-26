/* Copyright 2022 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.service.common;

public interface FlowHistoryCarrier {
    /**
     * Add a history record on the action.
     */
    void saveActionToHistory(String action, String description);

    /**
     * Add a history record on the error.
     */
    void saveErrorToHistory(String action, String errorMessage);

    /**
     * Add a history record on the error.
     */
    void saveErrorToHistory(String errorMessage);
}
