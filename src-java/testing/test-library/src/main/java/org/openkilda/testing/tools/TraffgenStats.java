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

package org.openkilda.testing.tools;

import org.openkilda.testing.model.topology.TopologyDefinition.TraffGen;
import org.openkilda.testing.service.traffexam.OperationalException;
import org.openkilda.testing.service.traffexam.TraffExamService;
import org.openkilda.testing.service.traffexam.model.Address;
import org.openkilda.testing.service.traffexam.model.AddressStats;
import org.openkilda.testing.service.traffexam.model.Vlan;
import org.openkilda.testing.service.traffexam.networkpool.Inet4ValueException;

import java.util.ArrayList;
import java.util.List;

public class TraffgenStats implements AutoCloseable {

    private final Address address;
    private final TraffExamService examService;

    private TraffgenStats(TraffExamService examService, Address address) {
        this.examService = examService;
        this.address = address;
    }

    public TraffgenStats(TraffExamService examService, TraffGen tg, List<Integer> intVlanIds)
            throws OperationalException, Inet4ValueException {
        List<Vlan> vlanIds = new ArrayList<Vlan>();
        for (Integer vlanId : intVlanIds) {
            Vlan vlan = new Vlan(vlanId);
            vlanIds.add(vlan);
        }
        this.examService = examService;
        address = examService.allocateFreeAddress(examService.hostByName(tg.getName()), vlanIds);
    }

    static TraffgenStats fromExistingAddress(TraffExamService examService, Address address) {
        return new TraffgenStats(examService, address);
    }

    public AddressStats get() {
        return examService.getStats(address);
    }

    @Override
    public void close() {
        examService.releaseAddress(address);
    }
}
