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

package org.openkilda.wfm.topology.nbworker.services;

public class ValidationServiceTest {
    /*TODO: need to rewrite / adapt to wrappers
    @Test
    public void empty() {
        ValidationService validationService = new ValidationService(repositoryFactory().build());
        SyncRulesResponse response = validationService.validate(generateSwitchFlowEntries());
        assertTrue(response.getMissingRules().isEmpty());
        assertTrue(response.getProperRules().isEmpty());
        assertTrue(response.getExcessRules().isEmpty());
    }

    @Test
    public void simpleSegmentCookies() {
        ValidationService validationService =
                new ValidationService(repositoryFactory().withSegmentsCookies(2L, 3L).build());
        SyncRulesResponse response = validationService.validate(generateSwitchFlowEntries(1L, 2L));
        assertEquals(ImmutableList.of(3L), response.getMissingRules());
        assertEquals(ImmutableList.of(2L), response.getProperRules());
        assertEquals(ImmutableList.of(1L), response.getExcessRules());
    }

    @Test
    public void segmentAndIngressCookies() {
        ValidationService validationService =
                new ValidationService(repositoryFactory().withSegmentsCookies(2L).withIngressCookies(1L).build());
        SyncRulesResponse response = validationService.validate(generateSwitchFlowEntries(1L, 2L));
        assertTrue(response.getMissingRules().isEmpty());
        assertEquals(ImmutableSet.of(1L, 2L), new HashSet<>(response.getProperRules()));
        assertTrue(response.getExcessRules().isEmpty());
    }

    @Test
    public void ignoreDefaulRules() {
        ValidationService validationService = new ValidationService(repositoryFactory().build());
        SyncRulesResponse response = validationService.validate(generateSwitchFlowEntries(0x8000000000000002L));
        assertTrue(response.getMissingRules().isEmpty());
        assertTrue(response.getProperRules().isEmpty());
        assertTrue(response.getExcessRules().isEmpty());
    }

    private SwitchFlowEntries generateSwitchFlowEntries(long... cookies) {
        List<FlowEntry> flowEntryList = new ArrayList<>(cookies.length);
        for (long cookie: cookies) {
            flowEntryList.add(new FlowEntry(cookie, 0, 0, 0, 0, "", 0, 0, 0, 0, null, null, null));
        }
        return new SwitchFlowEntries(new SwitchId("00:10"), flowEntryList);
    }

    private RepositoryFactoryBuilder repositoryFactory() {
        return new RepositoryFactoryBuilder();
    }

    private static class RepositoryFactoryBuilder {
        private long[] segmentsCookies = new long[0];
        private long[] ingressCookies = new long[0];

        private RepositoryFactoryBuilder withSegmentsCookies(long... cookies) {
            segmentsCookies = cookies;
            return this;
        }

        private RepositoryFactoryBuilder withIngressCookies(long... cookies) {
            ingressCookies = cookies;
            return this;
        }

        private RepositoryFactory build() {
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
            FlowPathRepository flowSegmentRepository = mock(FlowPathRepository.class);
            when(flowSegmentRepository.findByDestSwitchId(any())).thenReturn(flowSegments);
            FlowRepository flowRepository = mock(FlowRepository.class);
            when(flowRepository.findBySrcSwitchId(any())).thenReturn(flows);
            RepositoryFactory repositoryFactory = mock(RepositoryFactory.class);
            when(repositoryFactory.createFlowSegmentRepository()).thenReturn(flowSegmentRepository);
            when(repositoryFactory.createFlowRepository()).thenReturn(flowRepository);
            return repositoryFactory;
        }
    }*/
}
