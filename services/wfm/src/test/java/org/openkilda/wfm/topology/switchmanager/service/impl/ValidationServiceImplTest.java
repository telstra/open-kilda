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

package org.openkilda.wfm.topology.switchmanager.service.impl;

public class ValidationServiceImplTest {
    /*TODO: need to rewrite / adapt to wrappers
    private static final SwitchId SWITCH_ID = new SwitchId("00:10");

    @Test
    public void empty() {
        ValidationService validationService = new ValidationServiceImpl(persistenceManager().build());
        ValidateRulesResult response = validationService.validate(SWITCH_ID, new HashSet<>());
        assertTrue(response.getMissingRules().isEmpty());
        assertTrue(response.getProperRules().isEmpty());
        assertTrue(response.getExcessRules().isEmpty());
    }

    @Test
    public void simpleSegmentCookies() {
        ValidationService validationService =
                new ValidationServiceImpl(persistenceManager().withSegmentsCookies(2L, 3L).build());
        ValidateRulesResult response = validationService.validate(SWITCH_ID, Sets.newHashSet(1L, 2L));
        assertEquals(ImmutableList.of(3L), response.getMissingRules());
        assertEquals(ImmutableList.of(2L), response.getProperRules());
        assertEquals(ImmutableList.of(1L), response.getExcessRules());
    }

    @Test
    public void segmentAndIngressCookies() {
        ValidationService validationService =
                new ValidationServiceImpl(persistenceManager().withSegmentsCookies(2L).withIngressCookies(1L).build());
        ValidateRulesResult response = validationService.validate(SWITCH_ID, Sets.newHashSet(1L, 2L));
        assertTrue(response.getMissingRules().isEmpty());
        assertEquals(ImmutableSet.of(1L, 2L), new HashSet<>(response.getProperRules()));
        assertTrue(response.getExcessRules().isEmpty());
    }

    @Test
    public void ignoreDefaulRules() {
        ValidationService validationService = new ValidationServiceImpl(persistenceManager().build());
        ValidateRulesResult response = validationService.validate(SWITCH_ID, Sets.newHashSet(0x8000000000000002L));
        assertTrue(response.getMissingRules().isEmpty());
        assertTrue(response.getProperRules().isEmpty());
        assertTrue(response.getExcessRules().isEmpty());
    }

    private PersistenceManagerBuilder persistenceManager() {
        return new PersistenceManagerBuilder();
    }

    private static class PersistenceManagerBuilder {
        private long[] segmentsCookies = new long[0];
        private long[] ingressCookies = new long[0];

        private PersistenceManagerBuilder withSegmentsCookies(long... cookies) {
            segmentsCookies = cookies;
            return this;
        }

        private PersistenceManagerBuilder withIngressCookies(long... cookies) {
            ingressCookies = cookies;
            return this;
        }

        private PersistenceManager build() {
            List<FlowSegment> flowSegments = new ArrayList<>(segmentsCookies.length);
            for (long cookie: segmentsCookies) {
                FlowSegment flowSegment = mock(FlowSegment.class);
                when(flowSegment.getCookie()).thenReturn(cookie);
                flowSegments.add(flowSegment);
            }
            List<Flow> flows = new ArrayList<>(ingressCookies.length);
            for (long cookie: ingressCookies) {
                Flow flow = mock(Flow.class);
                when(flow.getCookie()).thenReturn(cookie);
                flows.add(flow);
            }
            FlowSegmentRepository flowSegmentRepository = mock(FlowSegmentRepository.class);
            when(flowSegmentRepository.findByDestSwitchId(any())).thenReturn(flowSegments);
            FlowRepository flowRepository = mock(FlowRepository.class);
            when(flowRepository.findBySrcSwitchId(any())).thenReturn(flows);
            RepositoryFactory repositoryFactory = mock(RepositoryFactory.class);
            when(repositoryFactory.createFlowSegmentRepository()).thenReturn(flowSegmentRepository);
            when(repositoryFactory.createFlowRepository()).thenReturn(flowRepository);
            PersistenceManager persistenceManager = mock(PersistenceManager.class);
            when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);
            return persistenceManager;
        }
    }
    */
}
