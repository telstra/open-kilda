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

package org.openkilda.wfm.topology.flowhs.validation;

import static java.util.Collections.emptyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.command.yflow.SubFlowDto;
import org.openkilda.messaging.command.yflow.SubFlowSharedEndpointEncapsulation;
import org.openkilda.messaging.command.yflow.YFlowRequest;
import org.openkilda.messaging.error.InvalidFlowException;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowMirrorPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.PhysicalPortRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.repositories.YFlowRepository;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.Optional;

public class YFlowValidatorTest {
    public static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    public static final SwitchId SWITCH_ID_2 = new SwitchId(2);
    public static final SwitchId SWITCH_ID_3 = new SwitchId(3);
    public static final int PORT_1 = 101;
    public static final int PORT_2 = 102;
    public static final int PORT_3 = 103;

    public static YFlowValidator yFlowValidator;

    @BeforeClass
    public static void setup() {
        RepositoryFactory repositoryFactory = mock(RepositoryFactory.class);
        when(repositoryFactory.createFlowRepository()).thenReturn(mock(FlowRepository.class));
        SwitchRepository switchRepository = mock(SwitchRepository.class);
        when(repositoryFactory.createSwitchRepository()).thenReturn(switchRepository);
        when(repositoryFactory.createIslRepository()).thenReturn(mock(IslRepository.class));
        SwitchPropertiesRepository switchPropertiesRepository = mock(SwitchPropertiesRepository.class);
        when(repositoryFactory.createSwitchPropertiesRepository()).thenReturn(switchPropertiesRepository);
        when(repositoryFactory.createFlowMirrorPathRepository()).thenReturn(mock(FlowMirrorPathRepository.class));
        when(repositoryFactory.createYFlowRepository()).thenReturn(mock(YFlowRepository.class));
        PhysicalPortRepository physicalPortRepository = mock(PhysicalPortRepository.class);
        when(repositoryFactory.createPhysicalPortRepository()).thenReturn(physicalPortRepository);
        PersistenceManager persistenceManager = mock(PersistenceManager.class);
        when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);
        yFlowValidator = new YFlowValidator(persistenceManager);

        when(switchRepository.findById(SWITCH_ID_1))
                .thenReturn(Optional.of(Switch.builder().switchId(SWITCH_ID_1).ofDescriptionSoftware("")
                        .status(SwitchStatus.ACTIVE).build()));
        when(switchPropertiesRepository.findBySwitchId(SWITCH_ID_1))
                .thenReturn(Optional.of(SwitchProperties.builder().build()));
        when(switchRepository.findById(SWITCH_ID_2))
                .thenReturn(Optional.of(Switch.builder().switchId(SWITCH_ID_2).ofDescriptionSoftware("")
                        .status(SwitchStatus.ACTIVE).build()));
        when(switchPropertiesRepository.findBySwitchId(SWITCH_ID_2))
                .thenReturn(Optional.of(SwitchProperties.builder().build()));
        when(switchRepository.findById(SWITCH_ID_3))
                .thenReturn(Optional.of(Switch.builder().switchId(SWITCH_ID_3).ofDescriptionSoftware("")
                        .status(SwitchStatus.ACTIVE).build()));
        when(switchPropertiesRepository.findBySwitchId(SWITCH_ID_3))
                .thenReturn(Optional.of(SwitchProperties.builder().build()));

        when(physicalPortRepository.findBySwitchIdAndPortNumber(any(), anyInt())).thenReturn(Optional.empty());
    }

    @Test(expected = InvalidFlowException.class)
    public void failIfNoSubFlowsProvided()
            throws InvalidFlowException, UnavailableFlowEndpointException {
        YFlowRequest request = YFlowRequest.builder()
                .yFlowId("test")
                .sharedEndpoint(FlowEndpoint.builder()
                        .switchId(SWITCH_ID_1)
                        .portNumber(PORT_1)
                        .build())
                .subFlows(emptyList())
                .build();
        yFlowValidator.validate(request);
    }

    @Test
    public void passIfOneSwitchFlowRequested()
            throws InvalidFlowException, UnavailableFlowEndpointException {
        YFlowRequest request = YFlowRequest.builder()
                .yFlowId("test")
                .sharedEndpoint(FlowEndpoint.builder()
                        .switchId(SWITCH_ID_1)
                        .portNumber(PORT_1)
                        .build())
                .subFlows(Arrays.asList(SubFlowDto.builder()
                                .flowId("test_1")
                                .sharedEndpoint(new SubFlowSharedEndpointEncapsulation(1, 0))
                                .endpoint(FlowEndpoint.builder()
                                        .switchId(SWITCH_ID_1)
                                        .portNumber(PORT_2)
                                        .build())
                                .build(),
                        SubFlowDto.builder()
                                .flowId("test_2")
                                .sharedEndpoint(new SubFlowSharedEndpointEncapsulation(2, 0))
                                .endpoint(FlowEndpoint.builder()
                                        .switchId(SWITCH_ID_3)
                                        .portNumber(PORT_3)
                                        .build())
                                .build()))
                .build();
        yFlowValidator.validate(request);
    }

    @Test
    public void passIfOneSwitchFlowRequestedAsTheLast()
            throws InvalidFlowException, UnavailableFlowEndpointException {
        YFlowRequest request = YFlowRequest.builder()
                .yFlowId("test")
                .sharedEndpoint(FlowEndpoint.builder()
                        .switchId(SWITCH_ID_1)
                        .portNumber(PORT_1)
                        .build())
                .subFlows(Arrays.asList(SubFlowDto.builder()
                                .flowId("test_1")
                                .sharedEndpoint(new SubFlowSharedEndpointEncapsulation(1, 0))
                                .endpoint(FlowEndpoint.builder()
                                        .switchId(SWITCH_ID_2)
                                        .portNumber(PORT_2)
                                        .build())
                                .build(),
                        SubFlowDto.builder()
                                .flowId("test_2")
                                .sharedEndpoint(new SubFlowSharedEndpointEncapsulation(2, 0))
                                .endpoint(FlowEndpoint.builder()
                                        .switchId(SWITCH_ID_1)
                                        .portNumber(PORT_3)
                                        .build())
                                .build()))
                .build();
        yFlowValidator.validate(request);
    }

    @Test
    public void passIfBothOneSwitchFlowRequested()
            throws InvalidFlowException, UnavailableFlowEndpointException {
        YFlowRequest request = YFlowRequest.builder()
                .yFlowId("test")
                .sharedEndpoint(FlowEndpoint.builder()
                        .switchId(SWITCH_ID_1)
                        .portNumber(PORT_1)
                        .build())
                .subFlows(Arrays.asList(SubFlowDto.builder()
                                .flowId("test_1")
                                .sharedEndpoint(new SubFlowSharedEndpointEncapsulation(1, 0))
                                .endpoint(FlowEndpoint.builder()
                                        .switchId(SWITCH_ID_1)
                                        .portNumber(PORT_2)
                                        .build())
                                .build(),
                        SubFlowDto.builder()
                                .flowId("test_2")
                                .sharedEndpoint(new SubFlowSharedEndpointEncapsulation(2, 0))
                                .endpoint(FlowEndpoint.builder()
                                        .switchId(SWITCH_ID_1)
                                        .portNumber(PORT_3)
                                        .build())
                                .build()))
                .build();
        yFlowValidator.validate(request);
    }

    @Test(expected = InvalidFlowException.class)
    public void failIfNegativeBandwidthProvided()
            throws InvalidFlowException, UnavailableFlowEndpointException {
        YFlowRequest request = YFlowRequest.builder()
                .yFlowId("test")
                .maximumBandwidth(-1)
                .sharedEndpoint(FlowEndpoint.builder()
                        .switchId(SWITCH_ID_1)
                        .portNumber(PORT_1)
                        .build())
                .subFlows(Arrays.asList(SubFlowDto.builder()
                                .flowId("test_1")
                                .sharedEndpoint(new SubFlowSharedEndpointEncapsulation(1, 0))
                                .endpoint(FlowEndpoint.builder()
                                        .switchId(SWITCH_ID_2)
                                        .portNumber(PORT_2)
                                        .build())
                                .build(),
                        SubFlowDto.builder()
                                .flowId("test_2")
                                .sharedEndpoint(new SubFlowSharedEndpointEncapsulation(2, 0))
                                .endpoint(FlowEndpoint.builder()
                                        .switchId(SWITCH_ID_3)
                                        .portNumber(PORT_3)
                                        .build())
                                .build()))
                .build();
        yFlowValidator.validate(request);
    }

    private YFlowRequest getTestRequestWithMaxLatencyAndMaxLatencyTier2(Long maxLatency, Long maxLatencyTier2) {
        return YFlowRequest.builder()
                .yFlowId("test")
                .maxLatency(maxLatency)
                .maxLatencyTier2(maxLatencyTier2)
                .sharedEndpoint(FlowEndpoint.builder()
                        .switchId(SWITCH_ID_1)
                        .portNumber(PORT_1)
                        .build())
                .subFlows(Arrays.asList(SubFlowDto.builder()
                                .flowId("test_1")
                                .sharedEndpoint(new SubFlowSharedEndpointEncapsulation(1, 0))
                                .endpoint(FlowEndpoint.builder()
                                        .switchId(SWITCH_ID_2)
                                        .portNumber(PORT_2)
                                        .build())
                                .build(),
                        SubFlowDto.builder()
                                .flowId("test_2")
                                .sharedEndpoint(new SubFlowSharedEndpointEncapsulation(2, 0))
                                .endpoint(FlowEndpoint.builder()
                                        .switchId(SWITCH_ID_3)
                                        .portNumber(PORT_3)
                                        .build())
                                .build()))
                .build();
    }

    @Test(expected = InvalidFlowException.class)
    public void failIfMaxLatencyTier2HigherThanMaxLatency()
            throws InvalidFlowException, UnavailableFlowEndpointException {
        YFlowRequest request = getTestRequestWithMaxLatencyAndMaxLatencyTier2((long) 1000, (long) 500);
        yFlowValidator.validate(request);
    }

    @Test (expected = InvalidFlowException.class)
    public void failIfMaxLatencyTier2butMaxLatencyIsNull()
            throws InvalidFlowException, UnavailableFlowEndpointException {
        YFlowRequest request = getTestRequestWithMaxLatencyAndMaxLatencyTier2(null, (long) 500);
        yFlowValidator.validate(request);
    }

    @Test
    public void passIfMaxLatencyTier2butMaxLatencyIsNull()
            throws InvalidFlowException, UnavailableFlowEndpointException {
        YFlowRequest request = getTestRequestWithMaxLatencyAndMaxLatencyTier2(null, null);
        yFlowValidator.validate(request);
    }

    @Test
    public void passIfMaxLatencyTier2EqualToMaxLatency()
            throws InvalidFlowException, UnavailableFlowEndpointException {
        YFlowRequest request = getTestRequestWithMaxLatencyAndMaxLatencyTier2(500L, 500L);
        yFlowValidator.validate(request);
    }

    @Test(expected = InvalidFlowException.class)
    public void failIfNoSharedEndpointProvided()
            throws InvalidFlowException, UnavailableFlowEndpointException {
        YFlowRequest request = YFlowRequest.builder()
                .yFlowId("test")
                .subFlows(Arrays.asList(SubFlowDto.builder()
                                .flowId("test_1")
                                .sharedEndpoint(new SubFlowSharedEndpointEncapsulation(1, 0))
                                .endpoint(FlowEndpoint.builder()
                                        .switchId(SWITCH_ID_2)
                                        .portNumber(PORT_2)
                                        .build())
                                .build(),
                        SubFlowDto.builder()
                                .flowId("test_2")
                                .sharedEndpoint(new SubFlowSharedEndpointEncapsulation(2, 0))
                                .endpoint(FlowEndpoint.builder()
                                        .switchId(SWITCH_ID_3)
                                        .portNumber(PORT_3)
                                        .build())
                                .build()))
                .build();
        yFlowValidator.validate(request);
    }

    @Test(expected = InvalidFlowException.class)
    public void failIfSubFlowHasNoSharedEndpointProvided()
            throws InvalidFlowException, UnavailableFlowEndpointException {
        YFlowRequest request = YFlowRequest.builder()
                .yFlowId("test")
                .sharedEndpoint(FlowEndpoint.builder()
                        .switchId(SWITCH_ID_1)
                        .portNumber(PORT_1)
                        .build())
                .subFlows(Arrays.asList(SubFlowDto.builder()
                                .flowId("test_1")
                                .endpoint(FlowEndpoint.builder()
                                        .switchId(SWITCH_ID_2)
                                        .portNumber(PORT_2)
                                        .build())
                                .build(),
                        SubFlowDto.builder()
                                .flowId("test_2")
                                .sharedEndpoint(new SubFlowSharedEndpointEncapsulation(2, 0))
                                .endpoint(FlowEndpoint.builder()
                                        .switchId(SWITCH_ID_3)
                                        .portNumber(PORT_3)
                                        .build())
                                .build()))
                .build();
        yFlowValidator.validate(request);
    }

    @Test(expected = InvalidFlowException.class)
    public void failIfSubFlowHasNoEndpointProvided()
            throws InvalidFlowException, UnavailableFlowEndpointException {
        YFlowRequest request = YFlowRequest.builder()
                .yFlowId("test")
                .sharedEndpoint(FlowEndpoint.builder()
                        .switchId(SWITCH_ID_1)
                        .portNumber(PORT_1)
                        .build())
                .subFlows(Arrays.asList(SubFlowDto.builder()
                                .flowId("test_1")
                                .sharedEndpoint(new SubFlowSharedEndpointEncapsulation(1, 0))
                                .build(),
                        SubFlowDto.builder()
                                .flowId("test_2")
                                .sharedEndpoint(new SubFlowSharedEndpointEncapsulation(2, 0))
                                .endpoint(FlowEndpoint.builder()
                                        .switchId(SWITCH_ID_3)
                                        .portNumber(PORT_3)
                                        .build())
                                .build()))
                .build();
        yFlowValidator.validate(request);
    }

    @Test(expected = InvalidFlowException.class)
    public void failIfSubFlowHasNoId()
            throws InvalidFlowException, UnavailableFlowEndpointException {
        YFlowRequest request = YFlowRequest.builder()
                .yFlowId("test")
                .sharedEndpoint(FlowEndpoint.builder()
                        .switchId(SWITCH_ID_1)
                        .portNumber(PORT_1)
                        .build())
                .subFlows(Arrays.asList(SubFlowDto.builder()
                                .sharedEndpoint(new SubFlowSharedEndpointEncapsulation(1, 0))
                                .endpoint(FlowEndpoint.builder()
                                        .switchId(SWITCH_ID_2)
                                        .portNumber(PORT_2)
                                        .build())
                                .build(),
                        SubFlowDto.builder()
                                .flowId("test_2")
                                .sharedEndpoint(new SubFlowSharedEndpointEncapsulation(2, 0))
                                .endpoint(FlowEndpoint.builder()
                                        .switchId(SWITCH_ID_3)
                                        .portNumber(PORT_3)
                                        .build())
                                .build()))
                .build();
        yFlowValidator.validate(request);
    }
}
