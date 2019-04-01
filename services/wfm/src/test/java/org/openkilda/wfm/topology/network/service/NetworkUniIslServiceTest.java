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

package org.openkilda.wfm.topology.network.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.model.Isl;
import org.openkilda.model.IslDownReason;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.share.mappers.IslMapper;
import org.openkilda.wfm.topology.network.model.Endpoint;
import org.openkilda.wfm.topology.network.model.IslDataHolder;
import org.openkilda.wfm.topology.network.model.IslReference;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class NetworkUniIslServiceTest {

    private final SwitchId alphaDatapath = new SwitchId(1);
    private final SwitchId betaDatapath = new SwitchId(2);
    @Mock
    private IUniIslCarrier carrier;

    @Before
    public void setup() {
        resetMocks();
    }

    private void resetMocks() {
        reset(carrier);
    }

    @Test
    public void newIslWithHistory() {
        NetworkUniIslService service = new NetworkUniIslService(carrier);
        Endpoint endpoint1 = Endpoint.of(alphaDatapath, 1);
        Endpoint endpoint2 = Endpoint.of(alphaDatapath, 2);

        Switch alphaSwitch = Switch.builder().switchId(alphaDatapath).build();
        Switch betaSwitch = Switch.builder().switchId(betaDatapath).build();

        Isl islAtoB = Isl.builder()
                .srcSwitch(alphaSwitch)
                .srcPort(1)
                .destSwitch(betaSwitch)
                .destPort(1).build();

        Isl islAtoB2 = Isl.builder()
                .srcSwitch(alphaSwitch)
                .srcPort(2)
                .destSwitch(betaSwitch)
                .destPort(2).build();


        service.uniIslSetup(endpoint1, islAtoB);
        service.uniIslSetup(endpoint2, islAtoB2);

        //System.out.println(mockingDetails(carrier).printInvocations());

        verify(carrier).setupIslFromHistory(endpoint1, IslReference.of(islAtoB), islAtoB);
        verify(carrier).setupIslFromHistory(endpoint2, IslReference.of(islAtoB2), islAtoB2);
    }

    @Test
    public void newIslFromUnknownToDownNoRemote() {
        NetworkUniIslService service = new NetworkUniIslService(carrier);
        Endpoint endpoint1 = Endpoint.of(alphaDatapath, 1);
        Endpoint endpoint2 = Endpoint.of(alphaDatapath, 2);

        service.uniIslSetup(endpoint1, null);
        service.uniIslSetup(endpoint2, null);

        service.uniIslFail(endpoint1);
        service.uniIslPhysicalDown(endpoint2);

        System.out.println(mockingDetails(carrier).printInvocations());
        verify(carrier, never()).notifyIslDown(any(Endpoint.class), any(IslReference.class), isA(IslDownReason.class));
    }


    @Test
    public void newIslFromUnknownToDownWithRemote() {
        NetworkUniIslService service = new NetworkUniIslService(carrier);
        Endpoint endpoint1 = Endpoint.of(alphaDatapath, 1);
        Endpoint endpoint2 = Endpoint.of(alphaDatapath, 2);
        Switch alphaSwitch = Switch.builder().switchId(alphaDatapath).build();
        Switch betaSwitch = Switch.builder().switchId(betaDatapath).build();


        Isl islAtoB = Isl.builder()
                .srcSwitch(alphaSwitch)
                .srcPort(1)
                .destSwitch(betaSwitch)
                .destPort(1).build();

        Isl islAtoB2 = Isl.builder()
                .srcSwitch(alphaSwitch)
                .srcPort(2)
                .destSwitch(betaSwitch)
                .destPort(2).build();

        service.uniIslSetup(endpoint1, islAtoB);
        service.uniIslSetup(endpoint2, islAtoB2);

        resetMocks();

        service.uniIslFail(endpoint1);
        service.uniIslPhysicalDown(endpoint2);


        System.out.println(mockingDetails(carrier).printInvocations());

        verify(carrier).notifyIslDown(endpoint1, IslReference.of(islAtoB), IslDownReason.POLL_TIMEOUT);
        verify(carrier).notifyIslDown(endpoint2, IslReference.of(islAtoB2), IslDownReason.PORT_DOWN);
    }

    @Test
    public void newIslFromUnknownToDownToUp() {
        NetworkUniIslService service = new NetworkUniIslService(carrier);
        Endpoint endpoint1 = Endpoint.of(alphaDatapath, 1);
        Endpoint endpoint2 = Endpoint.of(alphaDatapath, 2);

        Switch alphaSwitch = Switch.builder().switchId(alphaDatapath).build();
        Switch betaSwitch = Switch.builder().switchId(betaDatapath).build();

        Isl islAtoB = Isl.builder()
                .srcSwitch(alphaSwitch)
                .srcPort(1)
                .destSwitch(betaSwitch)
                .destPort(1).build();

        Isl islAtoB2 = Isl.builder()
                .srcSwitch(alphaSwitch)
                .srcPort(2)
                .destSwitch(betaSwitch)
                .destPort(2).build();

        Isl islAtoB3 = Isl.builder()
                .srcSwitch(alphaSwitch)
                .srcPort(1)
                .destSwitch(betaSwitch)
                .destPort(3).build();

        service.uniIslSetup(endpoint1, islAtoB);
        service.uniIslSetup(endpoint2, null);

        service.uniIslFail(endpoint1);
        service.uniIslPhysicalDown(endpoint2);

        resetMocks();

        IslInfoData disco1 = IslMapper.INSTANCE.map(islAtoB3);
        IslInfoData disco2 = IslMapper.INSTANCE.map(islAtoB2);

        service.uniIslDiscovery(endpoint1, disco1);
        service.uniIslDiscovery(endpoint2, disco2);

        System.out.println(mockingDetails(carrier).printInvocations());

        verify(carrier).notifyIslMove(endpoint1, IslReference.of(islAtoB));
        verify(carrier).notifyIslUp(endpoint1, IslReference.of(islAtoB3), new IslDataHolder(islAtoB2));
        verify(carrier).notifyIslUp(endpoint2, IslReference.of(islAtoB2), new IslDataHolder(islAtoB2));

    }

    @Test
    public void fromUnknownToUpAndDiscoveryWithMove() {
        NetworkUniIslService service = new NetworkUniIslService(carrier);
        Endpoint endpoint1 = Endpoint.of(alphaDatapath, 1);

        Switch alphaSwitch = Switch.builder().switchId(alphaDatapath).build();
        Switch betaSwitch = Switch.builder().switchId(betaDatapath).build();

        Isl islA1toB1 = Isl.builder()
                .srcSwitch(alphaSwitch)
                .srcPort(1)
                .destSwitch(betaSwitch)
                .destPort(1).build();

        Isl islA1toB3 = Isl.builder()
                .srcSwitch(alphaSwitch)
                .srcPort(1)
                .destSwitch(betaSwitch)
                .destPort(3).build();

        service.uniIslSetup(endpoint1, null);

        resetMocks();

        IslInfoData disco1 = IslMapper.INSTANCE.map(islA1toB1);
        IslInfoData disco2 = IslMapper.INSTANCE.map(islA1toB3);

        service.uniIslDiscovery(endpoint1, disco1);
        service.uniIslDiscovery(endpoint1, disco2);

        //System.out.println(mockingDetails(carrier).printInvocations());

        verify(carrier).notifyIslUp(endpoint1, IslReference.of(islA1toB1), new IslDataHolder(islA1toB1));
        verify(carrier).notifyIslMove(endpoint1, IslReference.of(islA1toB1));
        verify(carrier).notifyIslUp(endpoint1, IslReference.of(islA1toB3), new IslDataHolder(islA1toB3));
    }

    @Test
    public void fromUpToDown() {
        NetworkUniIslService service = new NetworkUniIslService(carrier);
        Endpoint endpoint1 = Endpoint.of(alphaDatapath, 1);
        Endpoint endpoint2 = Endpoint.of(alphaDatapath, 2);

        Switch alphaSwitch = Switch.builder().switchId(alphaDatapath).build();
        Switch betaSwitch = Switch.builder().switchId(betaDatapath).build();

        Isl islA1toB1 = Isl.builder()
                .srcSwitch(alphaSwitch)
                .srcPort(1)
                .destSwitch(betaSwitch)
                .destPort(1).build();

        Isl islA2toB2 = Isl.builder()
                .srcSwitch(alphaSwitch)
                .srcPort(2)
                .destSwitch(betaSwitch)
                .destPort(2).build();

        service.uniIslSetup(endpoint1, islA1toB1);
        service.uniIslSetup(endpoint2, islA2toB2);


        IslInfoData disco1 = IslMapper.INSTANCE.map(islA1toB1);
        IslInfoData disco2 = IslMapper.INSTANCE.map(islA2toB2);

        service.uniIslDiscovery(endpoint1, disco1);
        service.uniIslDiscovery(endpoint2, disco2);

        resetMocks();


        service.uniIslFail(endpoint1);
        service.uniIslPhysicalDown(endpoint2);

        //System.out.println(mockingDetails(carrier).printInvocations());

        verify(carrier).notifyIslDown(endpoint1, IslReference.of(islA1toB1), IslDownReason.POLL_TIMEOUT);
        verify(carrier).notifyIslDown(endpoint2, IslReference.of(islA2toB2), IslDownReason.PORT_DOWN);
    }

    @Test
    public void fromUptoBfdToUp() {
        NetworkUniIslService service = new NetworkUniIslService(carrier);
        Endpoint endpoint1 = Endpoint.of(alphaDatapath, 1);

        Switch alphaSwitch = Switch.builder().switchId(alphaDatapath).build();
        Switch betaSwitch = Switch.builder().switchId(betaDatapath).build();

        Isl islA1toB1 = Isl.builder()
                .srcSwitch(alphaSwitch)
                .srcPort(1)
                .destSwitch(betaSwitch)
                .destPort(1).build();

        service.uniIslSetup(endpoint1, islA1toB1);

        IslInfoData disco1 = IslMapper.INSTANCE.map(islA1toB1);

        service.uniIslDiscovery(endpoint1, disco1);

        resetMocks();

        service.uniIslBfdUpDown(endpoint1, true);
        service.uniIslBfdKill(endpoint1);

        //System.out.println(mockingDetails(carrier).printInvocations());

        verify(carrier, times(2)).notifyIslUp(endpoint1, IslReference.of(islA1toB1), new IslDataHolder(islA1toB1));
    }

    @Test
    public void fromUptoBfdToDown() {
        NetworkUniIslService service = new NetworkUniIslService(carrier);
        Endpoint endpoint1 = Endpoint.of(alphaDatapath, 1);

        Switch alphaSwitch = Switch.builder().switchId(alphaDatapath).build();
        Switch betaSwitch = Switch.builder().switchId(betaDatapath).build();

        Isl islA1toB1 = Isl.builder()
                .srcSwitch(alphaSwitch)
                .srcPort(1)
                .destSwitch(betaSwitch)
                .destPort(1).build();

        service.uniIslSetup(endpoint1, islA1toB1);

        IslInfoData disco1 = IslMapper.INSTANCE.map(islA1toB1);

        service.uniIslDiscovery(endpoint1, disco1);
        service.uniIslBfdUpDown(endpoint1, true);

        resetMocks();

        service.uniIslPhysicalDown(endpoint1);
        service.uniIslBfdUpDown(endpoint1, true);
        service.uniIslBfdUpDown(endpoint1, false);
        service.uniIslBfdUpDown(endpoint1, true);

        //System.out.println(mockingDetails(carrier).printInvocations());

        verify(carrier).notifyIslDown(endpoint1, IslReference.of(islA1toB1), IslDownReason.PORT_DOWN);
        verify(carrier, times(2)).notifyIslUp(endpoint1, IslReference.of(islA1toB1), new IslDataHolder(islA1toB1));
        verify(carrier).notifyIslDown(endpoint1, IslReference.of(islA1toB1), IslDownReason.BFD_DOWN);
    }
}
