/* Copyright 2021 Telstra Open Source
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

import static java.lang.String.format;

import org.openkilda.model.FlowMirrorPath;
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowProcessingFsm;

import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.AnonymousAction;

import java.util.Set;

@Slf4j
public abstract class FlowProcessingAction<T extends FlowProcessingFsm<T, S, E, C, ?>, S, E, C>
        extends AnonymousAction<T, S, E, C> {

    @Override
    public final void execute(S from, S to, E event, C context, T stateMachine) {
        try {
            perform(from, to, event, context, stateMachine);
        } catch (Exception ex) {
            String errorMessage = format("%s failed: %s", getClass().getSimpleName(), ex.getMessage());
            handleError(stateMachine, errorMessage, ex);
        }
    }

    protected abstract void perform(S from, S to, E event, C context, T stateMachine);

    protected void handleError(T stateMachine, String errorMessage, Exception ex) {
        stateMachine.fireError(errorMessage);
    }

    protected Set<SwitchId> getInvolvedSwitches(FlowMirrorPath mirrorPath) {
        Set<SwitchId> result = Sets.newHashSet(mirrorPath.getMirrorSwitchId(), mirrorPath.getEgressSwitchId());
        if (!mirrorPath.getSegments().isEmpty()) {
            result.add(mirrorPath.getSegments().get(0).getSrcSwitchId());
        }
        for (PathSegment segment : mirrorPath.getSegments()) {
            result.add(segment.getDestSwitchId());
        }
        return result;
    }
}
