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

import org.openkilda.messaging.command.switches.SwitchValidateRequest;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.info.meter.SwitchMeterEntries;
import org.openkilda.messaging.info.rule.SwitchExpectedDefaultFlowEntries;
import org.openkilda.messaging.info.rule.SwitchExpectedDefaultMeterEntries;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.messaging.info.rule.SwitchGroupEntries;

public interface SwitchValidateService {

    void handleSwitchValidateRequest(String key, SwitchValidateRequest data);

    void handleFlowEntriesResponse(String key, SwitchFlowEntries data);

    void handleGroupEntriesResponse(String key, SwitchGroupEntries data);

    void handleExpectedDefaultFlowEntriesResponse(String key, SwitchExpectedDefaultFlowEntries data);

    void handleExpectedDefaultMeterEntriesResponse(String key, SwitchExpectedDefaultMeterEntries data);

    void handleMeterEntriesResponse(String key, SwitchMeterEntries data);

    void handleMetersUnsupportedResponse(String key);

    void handleTaskTimeout(String key);

    void handleTaskError(String key, ErrorMessage message);

    void activate();

    boolean deactivate();

    boolean isAllOperationsCompleted();
}
