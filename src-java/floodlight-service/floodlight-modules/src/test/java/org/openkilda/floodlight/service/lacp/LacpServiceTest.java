/* Copyright 2022 Telstra Open Source
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

package org.openkilda.floodlight.service.lacp;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.openkilda.floodlight.KafkaChannel;
import org.openkilda.floodlight.KildaCore;
import org.openkilda.floodlight.KildaCoreConfig;
import org.openkilda.floodlight.service.kafka.KafkaUtilityService;
import org.openkilda.floodlight.service.of.InputService;
import org.openkilda.floodlight.shared.packet.Lacp;
import org.openkilda.floodlight.shared.packet.Lacp.ActorPartnerInfo;
import org.openkilda.floodlight.shared.packet.Lacp.CollectorInformation;
import org.openkilda.floodlight.shared.packet.Lacp.State;
import org.openkilda.floodlight.test.standard.PacketTestBase;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.projectfloodlight.openflow.types.MacAddress;

public class LacpServiceTest extends PacketTestBase {
    public static final int ACTOR_SYSTEM_PRIORITY = 1;
    public static final MacAddress ACTOR_SYSTEM_ID = MacAddress.of(2);
    public static final int ACTOR_KEY = 3;
    public static final int ACTOR_PORT_PRIORITY = 4;
    public static final int ACTOR_PORT_NUMBER = 5;

    public static final int PARTNER_SYSTEM_PRIORITY = 6;
    public static final MacAddress PARTNER_SYSTEM_ID = MacAddress.of(7);
    public static final int PARTNER_PORT_PRIORITY = 8;
    public static final byte COLLECTOR_TYPE = 9;
    public static final byte COLLECTOR_MAX_DELAY = 10;

    LacpService service;

    @Before
    public void setUp() {
        service = new LacpService();
        KafkaChannel kafkaChannel = mock(KafkaChannel.class);
        when(kafkaChannel.getRegion()).thenReturn("region");

        KafkaUtilityService kafkaUtilityService = mock(KafkaUtilityService.class);
        when(kafkaUtilityService.getKafkaChannel()).thenReturn(kafkaChannel);

        FloodlightModuleContext context = mock(FloodlightModuleContext.class);
        when(context.getServiceImpl(Mockito.eq(KafkaUtilityService.class))).thenReturn(kafkaUtilityService);

        KildaCoreConfig kildaCoreConfig = mock(KildaCoreConfig.class);
        when(kildaCoreConfig.getLacpSystemId()).thenReturn(PARTNER_SYSTEM_ID.toString());
        when(kildaCoreConfig.getLacpSystemPriority()).thenReturn(PARTNER_SYSTEM_PRIORITY);
        when(kildaCoreConfig.getLacpPortPriority()).thenReturn(PARTNER_PORT_PRIORITY);

        KildaCore kildaCore = mock(KildaCore.class);
        when(kildaCore.getConfig()).thenReturn(kildaCoreConfig);
        when(context.getServiceImpl(Mockito.eq(KildaCore.class))).thenReturn(kildaCore);

        InputService inputService = mock(InputService.class);
        doNothing().when(inputService).addTranslator(any(), any());
        when(context.getServiceImpl(Mockito.eq(InputService.class))).thenReturn(inputService);

        service.setup(context);
    }

    @Test
    public void serializeEthWithLacpTest() {
        State actorState = new State(true, true, true, false, true, false, true, true);
        ActorPartnerInfo actorInfo = new ActorPartnerInfo(
                ACTOR_SYSTEM_PRIORITY, ACTOR_SYSTEM_ID, ACTOR_KEY, ACTOR_PORT_PRIORITY, ACTOR_PORT_NUMBER, actorState);

        ActorPartnerInfo emptyPartnerInfo = new ActorPartnerInfo(0, MacAddress.of(0), 0, 0, 0, new State());
        Lacp.CollectorInformation collectorInformation = new CollectorInformation();
        collectorInformation.setType(COLLECTOR_TYPE);
        collectorInformation.setType(COLLECTOR_MAX_DELAY);

        Lacp lacpRequest = new Lacp();
        lacpRequest.setActor(actorInfo);
        lacpRequest.setPartner(emptyPartnerInfo);
        lacpRequest.setCollectorInformation(collectorInformation);

        Lacp lacpReply = service.modifyLacpRequest(lacpRequest);

        // we must copy Actor data to partner
        assertEquals(ACTOR_SYSTEM_PRIORITY, lacpReply.getPartner().getSystemPriority());
        assertEquals(ACTOR_SYSTEM_ID, lacpReply.getPartner().getSystemId());
        assertEquals(ACTOR_KEY, lacpReply.getPartner().getKey());
        assertEquals(ACTOR_PORT_PRIORITY, lacpReply.getPartner().getPortPriority());
        assertEquals(ACTOR_PORT_NUMBER, lacpReply.getPartner().getPortNumber());
        assertEquals(new State(true, true, true, false, true, false, true, true), lacpReply.getPartner().getState());

        // we must keep key and port number equal for actor and partner
        assertEquals(ACTOR_KEY, lacpReply.getActor().getKey());
        assertEquals(ACTOR_PORT_NUMBER, lacpReply.getActor().getPortNumber());

        assertEquals(PARTNER_SYSTEM_PRIORITY, lacpReply.getActor().getSystemPriority());
        assertEquals(PARTNER_SYSTEM_ID, lacpReply.getActor().getSystemId());
        assertEquals(PARTNER_PORT_PRIORITY, lacpReply.getActor().getPortPriority());
        assertEquals(new State(false, true, true, true, true, false, false, false), lacpReply.getActor().getState());
    }
}
