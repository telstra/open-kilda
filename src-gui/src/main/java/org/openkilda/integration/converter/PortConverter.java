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

package org.openkilda.integration.converter;

import org.openkilda.integration.source.store.dto.Port;
import org.openkilda.model.PortDiscrepancy;
import org.openkilda.model.PortInfo;

/**
 * The utility Class PortConverter.
 */
public final class PortConverter {

    /**
     * Instantiates a new port converter.
     */
    private PortConverter() {
    }

    /**
     * To port info.
     *
     * @param portInfo the port info
     * @param port     the inventory port
     * @return the port info
     */
    public static PortInfo toPortInfo(final PortInfo portInfo, final Port port) {

        PortDiscrepancy discrepancy = new PortDiscrepancy();
        discrepancy.setControllerDiscrepancy(true);
        discrepancy.setInventoryDiscrepancy(false);
        discrepancy.setAssignmentType(true);

        discrepancy.setControllerAssignmentType(null);
        discrepancy.setInventoryAssignmentType(port.getAssignmentType());
        portInfo.setDiscrepancy(discrepancy);

        appendInventoryInfo(portInfo, port);
        portInfo.setCustomeruuid(port.getCustomer().getCustomerUuid());
        portInfo.setPortNumber(String.valueOf(port.getPortNumber()));
        portInfo.setStatus(port.getStatus());

        return portInfo;
    }

    /**
     * Append inventory info.
     *
     * @param portInfo the port info
     * @param port     the port
     * @return the port info
     */
    public static PortInfo appendInventoryInfo(final PortInfo portInfo, final Port port) {

        portInfo.setUuid(port.getUuid());
        portInfo.setAssignmenttype(port.getAssignmentType());
        portInfo.setAssignmentState(port.getAssignmentState());
        portInfo.setAssignmentDate(port.getAssignmentDate());
        portInfo.setCrossconnect(port.getCrossConnect());
        portInfo.setPopLocation(port.getPopLocation());
        portInfo.setOdfMdf(port.getOdfMdf());
        portInfo.setNotes(port.getNotes());
        portInfo.setMmr(port.getMmr());
        portInfo.setIsActive(port.getIsActive());
        portInfo.setInventoryPortUuid(port.getInventoryPortUuid());
        portInfo.setCustomer(port.getCustomer());
        portInfo.setInterfacetype(port.getInterfaceType());

        return portInfo;
    }
}
