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

package org.openkilda.wfm.topology.flowhs.utils;

import org.openkilda.floodlight.api.request.rulemanager.BaseSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.OfCommand;
import org.openkilda.wfm.topology.flowhs.fsm.common.converters.OfCommandConverter;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class SpeakerRequestHelper {
    private SpeakerRequestHelper() {
    }

    /**
     * Keeps only failed commands in the speaker request. Removes excess dependencies.
     */
    public static void keepOnlyFailedCommands(BaseSpeakerCommandsRequest request, Set<UUID> failedCommandUuids) {
        List<OfCommand> failedCommands = request.getCommands().stream()
                .filter(ofCommand -> failedCommandUuids.contains(ofCommand.getUuid()))
                .collect(Collectors.toList());
        request.getCommands().clear();
        request.getCommands().addAll(OfCommandConverter.INSTANCE.removeExcessDependencies(failedCommands));
    }
}
