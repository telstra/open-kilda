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

package org.openkilda.wfm.topology.cache.service;

import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowInfoData;
import org.openkilda.messaging.info.flow.FlowOperation;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.FlowPair;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.model.FlowSegment;
import org.openkilda.model.Isl;
import org.openkilda.model.Switch;
import org.openkilda.pce.cache.FlowCache;
import org.openkilda.pce.cache.NetworkCache;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.TestSender;
import org.openkilda.wfm.topology.cache.StreamType;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class CacheServiceTest {

    private static final String THIRD_FLOW_ID = "third-flow";
    private static final FlowPair<Flow, Flow> THIRD_FLOW = new FlowPair<>(
            new Flow(THIRD_FLOW_ID, 10000, false, "", new SwitchId("ff:00"), 1, 2, new SwitchId("ff:00"), 1, 2),
            new Flow(THIRD_FLOW_ID, 10000, false, "", new SwitchId("ff:00"), 1, 2, new SwitchId("ff:00"), 1, 2));

    private CacheService cacheService;
    private TestSender sender;

    @Before
    public void init() {
        cacheService = new CacheService(new NetworkCache(), new FlowCache(), new TestFlowRepository(),
                new TestSwitchRepository(), new TestIslRepository());
        sender = new TestSender();
    }

    private class TestFlowRepository implements FlowRepository {
        @Override
        public Iterable<org.openkilda.model.Flow> findById(String flowId) {
            return Collections.emptyList();
        }

        @Override
        public Collection<org.openkilda.model.Flow> findAll() {
            return Collections.emptyList();
        }

        @Override
        public void createOrUpdate(org.openkilda.model.Flow entity) {
        }

        @Override
        public void delete(org.openkilda.model.Flow entity) {
        }
    }

    private class TestIslRepository implements IslRepository {
        @Override
        public Collection<Isl> findAllOrderedBySrcSwitch() {
            return Collections.emptyList();
        }

        @Override
        public Isl findByEndpoint(org.openkilda.model.SwitchId switchId, int port) {
            return null;
        }

        @Override
        public Iterable<Isl> findOccupiedByFlow(String flowId, boolean ignoreBandwidth, long requiredBandwidth) {
            return Collections.emptyList();
        }

        @Override
        public Iterable<Isl> findActiveWithAvailableBandwidth(boolean ignoreBandwidth, long requiredBandwidth) {
            return Collections.emptyList();
        }

        @Override
        public Collection<Isl> findAll() {
            return Collections.emptyList();
        }

        @Override
        public void createOrUpdate(Isl entity) {
        }

        @Override
        public void delete(Isl entity) {
        }
    }

    private class TestSwitchRepository implements SwitchRepository {
        @Override
        public Switch findBySwitchId(org.openkilda.model.SwitchId switchId) {
            return null;
        }

        @Override
        public Iterable<FlowSegment> findFlowSegmentsToSwitch(org.openkilda.model.SwitchId switchId) {
            return null;
        }

        @Override
        public Iterable<org.openkilda.model.Flow> findFlowsFromSwitch(org.openkilda.model.SwitchId switchId) {
            return null;
        }

        @Override
        public Collection<Switch> findAll() {
            return Collections.emptyList();
        }

        @Override
        public void createOrUpdate(Switch entity) {
        }

        @Override
        public void delete(Switch entity) {
        }
    }

    @Test
    public void cacheReceivesFlowTopologyUpdatesAndSendsToTopologyEngine() throws Exception {
        String correlationId = UUID.randomUUID().toString();
        FlowInfoData data = new FlowInfoData(THIRD_FLOW.getLeft().getFlowId(),
                THIRD_FLOW, FlowOperation.CREATE, correlationId);

        cacheService.handleFlowEvent(data, sender, correlationId);

        List<Object> messages = getTeMessages();

        Assert.assertEquals(1, messages.size());

        Object message = messages.get(0);

        Assert.assertTrue(message instanceof InfoMessage);

        Assert.assertEquals(THIRD_FLOW, ((FlowInfoData) ((InfoMessage) message).getData()).getPayload());
    }

    private List<Object> getTeMessages() {
        return sender.listMessages(StreamType.TPE.toString());
    }
}
