/* Copyright 2020 Telstra Open Source
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

package org.openkilda.server42.control.stormstub;

import org.openkilda.server42.control.messaging.flowrtt.AddFlow;
import org.openkilda.server42.control.messaging.flowrtt.ListFlowsResponse;
import org.openkilda.server42.control.messaging.flowrtt.PushSettings;
import org.openkilda.server42.control.messaging.islrtt.AddIsl;
import org.openkilda.server42.control.messaging.islrtt.ListIslsResponse;
import org.openkilda.server42.control.stormstub.api.AddFlowPayload;
import org.openkilda.server42.control.stormstub.api.AddIslPayload;
import org.openkilda.server42.control.stormstub.api.ListFlowsPayload;
import org.openkilda.server42.control.stormstub.api.ListIslsPayload;
import org.openkilda.server42.control.stormstub.api.PushSettingsPayload;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface ApiMapper {

    @Mapping(target = "headers", ignore = true)
    AddFlow map(AddFlowPayload payload);

    PushSettings map(PushSettingsPayload settingsPayload);

    ListFlowsPayload map(ListFlowsResponse listFlowsResponse);

    @Mapping(target = "headers", ignore = true)
    AddIsl map(AddIslPayload payload);

    ListIslsPayload map(ListIslsResponse listIslsResponse);
}
