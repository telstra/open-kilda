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

package org.openkilda.rulemanager.utils;

import org.openkilda.model.SwitchFeature;
import org.openkilda.rulemanager.utils.RoutingMetadata.HaSubFlowType;

import com.google.common.collect.Sets;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

public class RoutingMetadataTest {

    static final Set<SwitchFeature> FEATURES = Sets.newHashSet();

    @Test
    public void buildRoutingMetadata() {
        RoutingMetadata routingMetadata = RoutingMetadata.builder().build(FEATURES);

        Assertions.assertEquals(0x0000_0000_F000_0000L, routingMetadata.getMask());
        Assertions.assertEquals(0x0000_0000_1000_0000L, routingMetadata.getValue());
    }

    @Test
    public void buildRoutingMetadataHaFlow() {
        RoutingMetadata routingMetadata = RoutingMetadata.builder()
                .haSubFlowType(HaSubFlowType.HA_SUB_FLOW_1)
                .build(FEATURES);

        Assertions.assertEquals(0x0000_0000_F001_0000L, routingMetadata.getMask());
        Assertions.assertEquals(0x0000_0000_1000_0000L, routingMetadata.getValue());

        routingMetadata = RoutingMetadata.builder()
                .haSubFlowType(HaSubFlowType.HA_SUB_FLOW_2)
                .build(FEATURES);

        Assertions.assertEquals(0x0000_0000_F001_0000L, routingMetadata.getMask());
        Assertions.assertEquals(0x0000_0000_1001_0000L, routingMetadata.getValue());
    }

    @Test
    public void buildRoutingMetadataHaFlowWithOuterVlan() {
        RoutingMetadata routingMetadata = RoutingMetadata.builder().outerVlanId(1).build(FEATURES);

        Assertions.assertEquals(0x0000_0000_F000_FFF8L, routingMetadata.getMask());
        Assertions.assertEquals(0x0000_0000_1000_0018L, routingMetadata.getValue());

        routingMetadata = RoutingMetadata.builder()
                .haSubFlowType(HaSubFlowType.HA_SUB_FLOW_1)
                .outerVlanId(1)
                .build(FEATURES);

        Assertions.assertEquals(0x0000_0000_F001_FFF8L, routingMetadata.getMask());
        Assertions.assertEquals(0x0000_0000_1000_0018L, routingMetadata.getValue());
    }
}
