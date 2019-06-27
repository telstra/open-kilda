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

package org.openkilda.wfm.topology.network.storm.bolt.history;

import org.openkilda.messaging.info.event.PortInfoData;

import com.google.common.base.Objects;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class PortInfoDataAggregator {
    private Map<SwitchPort, PortInfoData> lastPortInfo = new HashMap<>();

    /**
     * Adding the port info to aggregator.
     *
     * @param portInfoData the port info.
     */
    public void add(PortInfoData portInfoData) {
        log.info("Adding port info for: {}_{} state {}", portInfoData.getSwitchId(), portInfoData.getPortNo(),
                portInfoData.getState());
        lastPortInfo.put(new SwitchPort(portInfoData), portInfoData);
    }

    public Collection<PortInfoData> getPortInfos() {
        return lastPortInfo.values();
    }

    private static class SwitchPort {
        final long switchId;
        final int portNo;

        SwitchPort(long switchId, int portNo) {
            this.switchId = switchId;
            this.portNo = portNo;
        }

        SwitchPort(PortInfoData portInfoData) {
            this(portInfoData.getSwitchId().getId(), portInfoData.getPortNo());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SwitchPort that = (SwitchPort) o;
            return switchId == that.switchId
                    && portNo == that.portNo;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(switchId, portNo);
        }
    }
}
