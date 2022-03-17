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

package org.openkilda.wfm.topology.flowhs.model.path;

import org.openkilda.model.PathId;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

import java.util.List;

// TODO(surabujin): better name?
@Value
@AllArgsConstructor
public class FlowPathRequest {
    @NonNull
    FlowPathReference reference;

    @NonNull
    List<FlowPathChunk> pathChunks;

    boolean canRevertOnError;

    public FlowPathRequest(@NonNull String flowId, @NonNull PathId pathId, @NonNull List<FlowPathChunk> pathChunks) {
        this(flowId, pathId, pathChunks, true);
    }

    @Builder
    public FlowPathRequest(
            @NonNull String flowId, @NonNull PathId pathId, @Singular @NonNull List<FlowPathChunk> pathChunks,
            boolean canRevertOnError) {
        this(new FlowPathReference(flowId, pathId), pathChunks, canRevertOnError);
    }

    public enum PathChunkType {
        INGRESS, NOT_INGRESS, ALL_AT_ONCE,
        REVERT
    }
}
