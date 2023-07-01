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

package org.openkilda.wfm.topology.ping.bolt.producer;

import static java.lang.String.format;

import org.openkilda.model.FlowPath;
import org.openkilda.model.PathSegment;

import java.util.List;

public final class ProducerUtils {

    private ProducerUtils() {}

    /**
     * Gets ISL port of the first segment.
     * @param flowPath flow path.
     * @return ISL port.
     */
    public static int getFirstIslPort(FlowPath flowPath) {
        List<PathSegment> segments = flowPath.getSegments();
        if (segments.isEmpty()) {
            throw new IllegalArgumentException(
                    format("Path segments not provided, flowPath: %s", flowPath));
        }

        PathSegment ingressSegment = segments.get(0);
        if (!ingressSegment.getSrcSwitchId().equals(flowPath.getSrcSwitchId())) {
            throw new IllegalStateException(
                    format("FlowSegment was not found for ingress flow rule, flowPath: %s", flowPath));
        }

        return ingressSegment.getSrcPort();
    }
}
