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

package org.openkilda.wfm.topology.switchmanager.service;

import org.openkilda.messaging.command.switches.SwitchRulesDeleteRequest;
import org.openkilda.messaging.command.switches.SwitchRulesInstallRequest;
import org.openkilda.messaging.info.switches.SwitchRulesResponse;

public interface SwitchRuleService {

    /**
     * handles remove rule request.
     * @param key key
     * @param data request payload
     */
    void deleteRules(String key, SwitchRulesDeleteRequest data);

    /**
     * handles rule response.
     * @param key key
     * @param response payload
     */
    void rulesResponse(String key, SwitchRulesResponse response);

    /**
     * handles install rule request.
     * @param key key
     * @param data request payload
     */
    void installRules(String key, SwitchRulesInstallRequest data);

    void activate();

    boolean deactivate();

    boolean isAllOperationsCompleted();
}
