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

package org.openkilda.testing.tools;

import org.openkilda.testing.model.topology.TopologyDefinition.TraffGen;
import org.openkilda.testing.service.traffexam.OperationalException;
import org.openkilda.testing.service.traffexam.TraffExamService;
import org.openkilda.testing.service.traffexam.model.Address;
import org.openkilda.testing.service.traffexam.model.LldpData;
import org.openkilda.testing.service.traffexam.networkpool.Inet4ValueException;

public class ConnectedDevice implements AutoCloseable {

    private Address address;
    private TraffExamService examService;

    public ConnectedDevice(TraffExamService examService, TraffGen tg, int vlanId) throws OperationalException,
            Inet4ValueException {
        this.examService = examService;
        address = examService.allocateFreeAddress(examService.hostByName(tg.getName()), vlanId);
    }

    public void sendLldp(LldpData data) {
        examService.sendLldp(address, data);
    }

    @Override
    public void close() {
        examService.releaseAddress(address);
    }
}
