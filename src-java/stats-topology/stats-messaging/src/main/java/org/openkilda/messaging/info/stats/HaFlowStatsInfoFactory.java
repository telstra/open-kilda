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

package org.openkilda.messaging.info.stats;

import org.openkilda.messaging.payload.flow.PathNodePayload;
import org.openkilda.messaging.payload.yflow.YFlowEndpointResources;
import org.openkilda.model.MeterId;
import org.openkilda.model.cookie.FlowSegmentCookie;

import lombok.NonNull;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class HaFlowStatsInfoFactory {
    private final YFlowStatsInfoBlank blank;
    private final List<SubFlowPathInfo> subPaths;

    public HaFlowStatsInfoFactory(
            @NonNull String yFlowId,
            @NonNull YFlowEndpointResources sharedEndpointResources,
            @NonNull YFlowEndpointResources yPointResources,
            YFlowEndpointResources protectedYPointResources, List<SubFlowPathInfo> subPaths) {
        blank = new YFlowStatsInfoBlank(yFlowId, sharedEndpointResources, yPointResources, protectedYPointResources);
        this.subPaths = subPaths;
    }

    public UpdateYFlowStatsInfo produceAddUpdateNotification() {
        return new UpdateYFlowStatsInfo(blank);
    }

    public RemoveYFlowStatsInfo produceRemoveYFlowStatsInfo() {
        return new RemoveYFlowStatsInfo(blank);
    }

    /**
     * Builds list of flow path update notifications.
     * These notifications contain information about y-point. This information is needed in stats topology to select
     * right source of rule stats for ingress and egress metrics.
     */
    public List<UpdateFlowPathInfo> produceUpdateSubFlowNotification() {
        List<UpdateFlowPathInfo> result = new ArrayList<>();
        for (SubFlowPathInfo path : subPaths) {
            result.add(new UpdateFlowPathInfo(path.flowId, blank.yFlowId, blank.yPointResources.getSwitchId(),
                    path.cookie, path.meterId, path.pathNodes, path.statVlans, path.ingressMirror, path.egressMirror));
        }
        return result;
    }

    private static class YFlowStatsInfoBlank extends BaseYFlowStatsInfo {
        public YFlowStatsInfoBlank(
                @NonNull String yFlowId,
                @NonNull YFlowEndpointResources sharedEndpointResources,
                @NonNull YFlowEndpointResources yPointResources,
                YFlowEndpointResources protectedYPointResources) {
            super(yFlowId, sharedEndpointResources, yPointResources, protectedYPointResources);
        }
    }

    @Value
    public static class SubFlowPathInfo {
        String flowId;
        FlowSegmentCookie cookie;
        MeterId meterId;
        List<PathNodePayload> pathNodes;
        Set<Integer> statVlans;
        boolean ingressMirror;
        boolean egressMirror;
    }
}
