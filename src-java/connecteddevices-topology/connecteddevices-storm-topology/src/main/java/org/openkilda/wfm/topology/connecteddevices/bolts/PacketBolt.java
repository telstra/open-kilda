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

package org.openkilda.wfm.topology.connecteddevices.bolts;

import static org.openkilda.wfm.topology.utils.KafkaRecordTranslator.FIELD_ID_PAYLOAD;

import org.openkilda.messaging.info.event.ArpInfoData;
import org.openkilda.messaging.info.event.ConnectedDevicePacketBase;
import org.openkilda.messaging.info.event.LldpInfoData;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.connecteddevices.service.PacketService;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.tuple.Tuple;

@Slf4j
public class PacketBolt extends AbstractBolt {
    private transient PacketService packetService;

    public PacketBolt(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void init() {
        packetService = new PacketService(persistenceManager);
    }

    @Override
    protected void handleInput(Tuple input) throws PipelineException {
        ConnectedDevicePacketBase data = pullValue(input, FIELD_ID_PAYLOAD, ConnectedDevicePacketBase.class);

        if (data instanceof LldpInfoData) {
            packetService.handleLldpData((LldpInfoData) data);
        } else if (data instanceof ArpInfoData) {
            packetService.handleArpData((ArpInfoData) data);
        } else {
            unhandledInput(input);
        }
    }
}
