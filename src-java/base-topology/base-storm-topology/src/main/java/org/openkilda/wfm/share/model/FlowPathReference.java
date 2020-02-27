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

package org.openkilda.wfm.share.model;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathId;

import lombok.Builder;
import lombok.Value;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * "Normalized" flow path reference. It keeps flow's path pairs in both directions plus flow this paths related to.
 *
 * <p>One of 2 paths can be null. During object creation it will sort paths to ensure that "path" field is always set
 * and oppositePath can be set or can be null.
 */
@Value
public class FlowPathReference {
    private Flow flow;
    private FlowPath path;
    private FlowPath oppositePath;

    private Boolean protectedPath;

    public FlowPathReference(Flow flow, FlowPath path, FlowPath oppositePath) {
        this(flow, path, oppositePath, checkIsPathProtected(flow, path, oppositePath));
    }

    @Builder(toBuilder = true)
    public FlowPathReference(Flow flow, FlowPath path, FlowPath oppositePath, Boolean protectedPath) {
        this.flow = flow;
        if (path == null) {
            this.path = oppositePath;
            this.oppositePath = null;
        } else {
            this.path = path;
            this.oppositePath = oppositePath;
        }

        if (this.path == null) {
            throw new IllegalArgumentException("Need at least one non null flow path");
        }

        this.protectedPath = protectedPath;
    }

    private static Boolean checkIsPathProtected(Flow flow, FlowPath... paths) {
        Set<PathId> protectedPaths =
                Stream.of(flow.getProtectedForwardPathId(), flow.getProtectedReversePathId())
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        if (protectedPaths.isEmpty()) {
            return false;
        }

        Boolean isProtected = null;
        for (FlowPath entry : paths) {
            if (entry != null) {
                isProtected = protectedPaths.contains(entry.getPathId());
                break;
            }
        }
        return isProtected;
    }
}
