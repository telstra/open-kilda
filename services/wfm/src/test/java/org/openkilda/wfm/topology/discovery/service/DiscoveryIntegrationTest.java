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

package org.openkilda.wfm.topology.discovery.service;

import org.openkilda.config.provider.ConfigurationProvider;
import org.openkilda.messaging.info.event.SwitchChangeType;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.model.SpeakerSwitchDescription;
import org.openkilda.messaging.model.SpeakerSwitchPortView;
import org.openkilda.messaging.model.SpeakerSwitchView;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.Neo4jConfig;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.spi.PersistenceProvider;
import org.openkilda.wfm.share.hubandspoke.TaskIdBasedKeyFactory;
import org.openkilda.wfm.topology.discovery.model.DiscoveryOptions;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.neo4j.ogm.testutil.TestServer;

import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(MockitoJUnitRunner.class)
public class DiscoveryIntegrationTest {
    private static TestServer dbTestServer;
    private static PersistenceManager persistenceManager;

    private static final DiscoveryOptions options = DiscoveryOptions.builder()
            .bfdEnabled(true)
            .bfdLogicalPortOffset(200)
            .build();

    private final SpeakerSwitchDescription switchDescription = SpeakerSwitchDescription.builder()
            .manufacturer("OF vendor A")
            .hardware("AHW-0")
            .software("AOS-1")
            .serialNumber("aabbcc")
            .datapath("OpenFlow switch AABBCC")
            .build();

    private final InetSocketAddress speakerInetAddress = new InetSocketAddress(
            Inet4Address.getByName("127.1.0.254"), 6653);

    private final SwitchId alphaDatapath = new SwitchId(1);
    private final InetSocketAddress alphaInetAddress = new InetSocketAddress(
            Inet4Address.getByName("127.1.0.1"), 32768);

    private final String alphaDescription = String.format("%s OF_13 %s",
                                                          switchDescription.getManufacturer(),
                                                          switchDescription.getSoftware());

    private DiscoverySwitchService switchService;
    private DiscoveryPortService portService;
    private DiscoveryBfdPortService bfdPortService;
    private DiscoveryUniIslService uniIslService;
    private DiscoveryIslService islService;

    private IntegrationCarrier integrationCarrier;

    public DiscoveryIntegrationTest() throws UnknownHostException {
        // This constructor is required to define exception list for field initializers.
    }

    @BeforeClass
    public static void setUpOnce() {
        dbTestServer = new TestServer(true, true, 5);
        persistenceManager = PersistenceProvider.getInstance().createPersistenceManager(
                new ConfigurationProvider() { //NOSONAR
                    @SuppressWarnings("unchecked")
                    @Override
                    public <T> T getConfiguration(Class<T> configurationType) {
                        if (configurationType.equals(Neo4jConfig.class)) {
                            return (T) new Neo4jConfig() {
                                @Override
                                public String getUri() {
                                    return dbTestServer.getUri();
                                }

                                @Override
                                public String getLogin() {
                                    return dbTestServer.getUsername();
                                }

                                @Override
                                public String getPassword() {
                                    return dbTestServer.getPassword();
                                }

                                @Override
                                public int getConnectionPoolSize() {
                                    return 50;
                                }

                                @Override
                                public String getIndexesAuto() {
                                    return "update";
                                }
                            };
                        } else {
                            throw new UnsupportedOperationException("Unsupported configurationType "
                                                                            + configurationType);
                        }
                    }
                });
    }

    @Before
    public void setUp() throws Exception {
        switchService = new DiscoverySwitchService(null, persistenceManager, options.getBfdLogicalPortOffset());
        portService = new DiscoveryPortService(null);
        bfdPortService = new DiscoveryBfdPortService(persistenceManager, new TaskIdBasedKeyFactory(0));
        uniIslService = new DiscoveryUniIslService(null);
        islService = new DiscoveryIslService(persistenceManager);

        integrationCarrier = new IntegrationCarrier(
                switchService,
                portService, bfdPortService,
                uniIslService, islService);
    }

    @Test
    @Ignore
    public void switchAdd() {
        Set<SpeakerSwitchView.Feature> features = new HashSet<>();
        features.add(SpeakerSwitchView.Feature.BFD);

        Integer bfdLocalPortOffset = options.getBfdLogicalPortOffset();
        List<SpeakerSwitchPortView> ports = ImmutableList.of(
                new SpeakerSwitchPortView(1, SpeakerSwitchPortView.State.UP),
                new SpeakerSwitchPortView(1 + bfdLocalPortOffset, SpeakerSwitchPortView.State.UP),
                new SpeakerSwitchPortView(2, SpeakerSwitchPortView.State.DOWN),
                new SpeakerSwitchPortView(2 + bfdLocalPortOffset, SpeakerSwitchPortView.State.DOWN));
        SpeakerSwitchView speakerSwitchView = new SpeakerSwitchView(
                alphaDatapath, alphaInetAddress, speakerInetAddress, "OF_13", switchDescription, features, ports);

        SwitchInfoData switchAddEvent = new SwitchInfoData(
                alphaDatapath, SwitchChangeType.ADDED,
                alphaInetAddress.toString(), alphaInetAddress.toString(), alphaDescription,
                speakerInetAddress.toString(),
                false,
                speakerSwitchView);
        switchService.switchEvent(switchAddEvent);

        SwitchInfoData switchActivateEvent = new SwitchInfoData(
                alphaDatapath, SwitchChangeType.ACTIVATED,
                alphaInetAddress.toString(), alphaInetAddress.toString(), alphaDescription,
                speakerInetAddress.toString(),
                false,
                speakerSwitchView);
        switchService.switchEvent(switchActivateEvent);
    }
}
