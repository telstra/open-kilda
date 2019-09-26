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

package org.openkilda.wfm.topology.connecteddevices.service;

import static org.openkilda.model.ConnectedDeviceType.LLDP;

import org.openkilda.messaging.info.event.LldpInfoData;
import org.openkilda.model.ConnectedDevice;
import org.openkilda.model.Cookie;
import org.openkilda.model.FlowCookie;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.ConnectedDeviceRepository;
import org.openkilda.persistence.repositories.FlowCookieRepository;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Optional;

@Slf4j
public class PacketService {
    private TransactionManager transactionManager;
    private FlowCookieRepository flowCookieRepository;
    private ConnectedDeviceRepository connectedDeviceRepository;

    public PacketService(PersistenceManager persistenceManager) {
        transactionManager = persistenceManager.getTransactionManager();
        flowCookieRepository = persistenceManager.getRepositoryFactory().createFlowCookieRepository();
        connectedDeviceRepository = persistenceManager.getRepositoryFactory().createConnectedDeviceRepository();
    }

    /**
     * Handle LLDP info data.
     */
    public void handleLldpData(LldpInfoData data) {
        transactionManager.doInTransaction(() -> {
            Cookie cookie = new Cookie(data.getCookie());
            Optional<FlowCookie> flowCookie = flowCookieRepository.findByCookie(cookie.getUnmaskedValue());
            if (!flowCookie.isPresent()) {
                log.warn("Couldn't find flow by cookie {}", cookie);
                return;
            }

            String flowId = flowCookie.get().getFlowId();
            boolean isForward = cookie.isMaskedAsForward();

            ConnectedDevice device = connectedDeviceRepository
                    .findByUniqueFieldCombination(
                            flowId, isForward, data.getMacAddress(), LLDP, data.getChassisId(), data.getPortId())
                    .orElse(ConnectedDevice.builder()
                            .flowId(flowId)
                            .source(isForward)
                            .macAddress(data.getMacAddress())
                            .timeFirstSeen(Instant.now())
                            .type(LLDP)
                            .chassisId(data.getChassisId())
                            .portId(data.getPortId())
                            .build());

            device.setTtl(data.getTtl());
            device.setPortDescription(data.getPortDescription());
            device.setSystemName(data.getSystemName());
            device.setSystemDescription(data.getSystemDescription());
            device.setSystemCapabilities(data.getSystemCapabilities());
            device.setManagementAddress(data.getManagementAddress());
            device.setTimeLastSeen(Instant.now());
            device.setType(LLDP);

            connectedDeviceRepository.createOrUpdate(device);
        });
    }
}
