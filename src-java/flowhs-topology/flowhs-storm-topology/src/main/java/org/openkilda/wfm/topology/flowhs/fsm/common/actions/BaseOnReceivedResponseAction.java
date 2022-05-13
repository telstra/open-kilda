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

package org.openkilda.wfm.topology.flowhs.fsm.common.actions;

import org.openkilda.floodlight.api.request.rulemanager.FlowCommand;
import org.openkilda.floodlight.api.request.rulemanager.GroupCommand;
import org.openkilda.floodlight.api.request.rulemanager.MeterCommand;
import org.openkilda.floodlight.api.request.rulemanager.OfCommand;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowProcessingWithHistorySupportFsm;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public abstract class BaseOnReceivedResponseAction<T extends FlowProcessingWithHistorySupportFsm<T, S, E, C, ?, ?>,
        S, E, C> extends HistoryRecordingAction<T, S, E, C> {
    protected final int speakerCommandRetriesLimit;

    protected BaseOnReceivedResponseAction(int speakerCommandRetriesLimit) {
        this.speakerCommandRetriesLimit = speakerCommandRetriesLimit;
    }

    protected Collection<OfCommand> filterOfCommands(Collection<OfCommand> source, Set<UUID> commandUuids) {
        return source.stream()
                .filter(ofCommand -> ofCommand instanceof FlowCommand
                        && commandUuids.contains(((FlowCommand) ofCommand).getData().getUuid())
                        || ofCommand instanceof MeterCommand
                        && commandUuids.contains(((MeterCommand) ofCommand).getData().getUuid())
                        || ofCommand instanceof GroupCommand
                        && commandUuids.contains(((GroupCommand) ofCommand).getData().getUuid()))
                .collect(Collectors.toList());
    }
}
