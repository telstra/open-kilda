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

package org.openkilda.messaging.command.switches;

    import com.fasterxml.jackson.annotation.JsonInclude;
import org.openkilda.messaging.command.CommandData;

import static com.google.common.base.MoreObjects.toStringHelper;
import static org.openkilda.messaging.Utils.TIMESTAMP;

/**
 * This request is used to set the global policy for switch connections, wrt default rule
 * installation.
 *
 * It can be used for set and get (PUT and GET). If the mode is NULL, the effect is a GET.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConnectModeRequest extends CommandData {

    /**
     * Describes what to do about the switch default rules.
     */
    public enum Mode {
        AUTO,   // Default rules will be added automatically, with no traffic evaluation
        SAFE,   // Default rules will be added "safely" .. ie evaluate network traffic
        MANUAL  // No default rules will be added when a switch connects
    }

    private Mode mode;

    /**
     * Constructs a connect mode request.
     *
     * @param mode what mode to set.
     */
    public ConnectModeRequest(Mode mode) {
        this.mode = mode;
    }

    public Mode getMode() {
        return mode;
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add(TIMESTAMP, timestamp)
                .add("mode", mode)
                .toString();
    }
}
