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

import org.openkilda.messaging.payload.yflow.YFlowEndpointResources;

import lombok.NonNull;

public class YFlowStatsInfoFactory {
    private final YFlowStatsInfoBlank blank;

    public YFlowStatsInfoFactory(
            @NonNull String yFlowId,
            @NonNull YFlowEndpointResources sharedEndpointResources,
            @NonNull YFlowEndpointResources yPointResources) {
        blank = new YFlowStatsInfoBlank(yFlowId, sharedEndpointResources, yPointResources);
    }

    public UpdateYFlowStatsInfo produceAddUpdateNotification() {
        return new UpdateYFlowStatsInfo(blank);
    }

    public RemoveYFlowStatsInfo produceRemoveYFlowStatsInfo() {
        return new RemoveYFlowStatsInfo(blank);
    }

    private static class YFlowStatsInfoBlank extends BaseYFlowStatsInfo {
        public YFlowStatsInfoBlank(
                @NonNull String yFlowId,
                @NonNull YFlowEndpointResources sharedEndpointResources,
                @NonNull YFlowEndpointResources yPointResources) {
            super(yFlowId, sharedEndpointResources, yPointResources);
        }
    }
}
