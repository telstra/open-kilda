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

package org.openkilda.pce;

import org.openkilda.model.SwitchId;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;

import java.io.Serializable;
import java.util.List;

@Value
@Builder
public class Path implements Serializable {
    private static final long serialVersionUID = 1L;

    @NonNull
    SwitchId srcSwitchId;

    @NonNull
    SwitchId destSwitchId;

    /**
     * Latency value in nseconds.
     */
    long latency;

    Long minAvailableBandwidth;

    @NonNull
    List<Segment> segments;

    boolean isBackupPath;

    @Value
    @Builder(toBuilder = true)
    @EqualsAndHashCode
    public static class Segment implements Serializable {
        private static final long serialVersionUID = 1L;

        @NonNull
        SwitchId srcSwitchId;

        @NonNull
        SwitchId destSwitchId;

        int srcPort;

        int destPort;

        /**
         * Segment latency value in nseconds.
         */
        long latency;

        public boolean areEndpointsEqual(Segment segment) {
            return srcSwitchId.equals(segment.srcSwitchId) && destSwitchId.equals(segment.destSwitchId)
                    && srcPort == segment.srcPort && destPort == segment.destPort;
        }
    }
}
