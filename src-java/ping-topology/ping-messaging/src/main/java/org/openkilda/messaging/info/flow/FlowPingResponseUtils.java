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

package org.openkilda.messaging.info.flow;

import java.util.List;

/**
 * Utility class for {@link FlowPingResponse}.
 */
public final class FlowPingResponseUtils {

    private FlowPingResponseUtils() {}

    /**
     * Build the ping success field.
     *
     * @param error error message
     * @param subFlows list of {@link SubFlowPingPayload}
     * @return true if the error string is not null and subflows ping was successful, false otherwise
     */
    public static boolean buildPingSuccess(String error, List<SubFlowPingPayload> subFlows) {
        if (error != null) {
            return false;
        }
        if (subFlows != null) {
            for (SubFlowPingPayload subFlow : subFlows) {
                if (!subFlow.getForward().isPingSuccess() || !subFlow.getReverse().isPingSuccess()) {
                    return false;
                }
            }
        }
        return true;
    }
}
