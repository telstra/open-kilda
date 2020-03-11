/* Copyright 2018 Telstra Open Source
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

package org.openkilda.atdd.staging.helpers;

import static java.lang.String.format;
import static org.hamcrest.Matchers.equalTo;

import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.northbound.dto.v1.switches.SwitchDto;
import org.openkilda.testing.model.topology.TopologyDefinition;
import org.openkilda.testing.model.topology.TopologyDefinition.Isl;
import org.openkilda.testing.model.topology.TopologyDefinition.Switch;
import org.openkilda.testing.service.floodlight.model.SwitchEntry;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.beans.HasPropertyWithValue;

public final class TopologyChecker {

    /**
     * Check whether the link equals the link definition.
     *
     * @param linkDef the link definition
     * @param islInfoData the link to match
     * @return whether the link matches the definition
     */
    public static boolean isIslEqual(TopologyDefinition.Isl linkDef, IslInfoData islInfoData) {
        PathNode discoveredSrcNode = islInfoData.getSource();
        PathNode discoveredDstNode = islInfoData.getDestination();
        return discoveredSrcNode.getSwitchId().equals(linkDef.getSrcSwitch().getDpId())
                && discoveredSrcNode.getPortNo() == linkDef.getSrcPort()
                && discoveredDstNode.getSwitchId().equals(linkDef.getDstSwitch().getDpId())
                && discoveredDstNode.getPortNo() == linkDef.getDstPort();
    }

    public static class SwitchMatcher extends HasPropertyWithValue<SwitchDto> {

        private Switch sw;

        public SwitchMatcher(Switch sw) {
            super("switchId", equalTo(sw.getDpId()));
            this.sw = sw;
        }

        @Override
        public void describeTo(Description description) {
            description.appendValue(format("%s %s", sw.getName(), sw.getDpId()));
        }
    }

    public static class SwitchEntryMatcher extends HasPropertyWithValue<SwitchEntry> {

        private Switch sw;

        public SwitchEntryMatcher(Switch sw) {
            super("switchId", equalTo(sw.getDpId()));
            this.sw = sw;
        }

        @Override
        public void describeTo(Description description) {
            description.appendValue(format("%s %s", sw.getName(), sw.getDpId()));
        }
    }

    public static class IslMatcher extends BaseMatcher<IslInfoData> {

        private Isl isl;

        public IslMatcher(Isl isl) {
            this.isl = isl;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText(format("%s %s-%d => %s %s-%d",
                    isl.getSrcSwitch().getName(), isl.getSrcSwitch().getDpId(), isl.getSrcPort(),
                    isl.getDstSwitch().getName(), isl.getDstSwitch().getDpId(), isl.getDstPort()));
        }

        @Override
        public boolean matches(Object actualValue) {
            return TopologyChecker.isIslEqual(isl, (IslInfoData) actualValue);
        }
    }

    private TopologyChecker() {
    }
}
