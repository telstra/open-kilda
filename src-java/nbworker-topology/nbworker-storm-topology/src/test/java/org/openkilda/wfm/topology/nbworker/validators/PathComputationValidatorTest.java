/* Copyright 2023 Telstra Open Source
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

package org.openkilda.wfm.topology.nbworker.validators;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.AvailableNetworkFactory;
import org.openkilda.pce.GetPathsResult;
import org.openkilda.pce.Path;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.pce.finder.FailReason;
import org.openkilda.pce.finder.FailReasonType;
import org.openkilda.pce.impl.AvailableNetwork;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

class PathComputationValidatorTest {

    public static final SwitchId SWITCH_ID_1 = new SwitchId("00:01");
    public static final SwitchId SWITCH_ID_2 = new SwitchId("00:02");
    @Mock
    private AvailableNetworkFactory availableNetworkFactory;

    @Mock
    PathComputer pathComputer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void whenInvalidPath_pceResponseContainsError() throws UnroutableFlowException {
        when(pathComputer.getPath((AvailableNetwork) any(), any(), anyBoolean())).thenReturn(GetPathsResult
                .builder()
                .failReasons(Collections.singletonMap(FailReasonType.NO_CONNECTION,
                                new FailReason(FailReasonType.NO_CONNECTION)))
                .build());
        PathComputationValidator pathComputationValidator =
                new PathComputationValidator(availableNetworkFactory, pathComputer);

        GetPathsResult result = pathComputationValidator.validatePath(getFlow(), getPath(), Collections.emptyList());

        assertFalse(result.isSuccess());
        assertEquals("[There is no connection]", result.getFailReasons().values().toString());
    }

    @Test
    void whenInvalidPath_andPceThrows_responseContainsError() throws UnroutableFlowException {
        when(pathComputer.getPath((AvailableNetwork) any(), any(), anyBoolean()))
                .thenThrow(new UnroutableFlowException("test exception message"));
        PathComputationValidator pathComputationValidator =
                new PathComputationValidator(availableNetworkFactory, pathComputer);

        assertDoesNotThrow(() ->
                pathComputationValidator.validatePath(getFlow(), getPath(), Collections.emptyList()));
        GetPathsResult result = pathComputationValidator.validatePath(getFlow(), getPath(), Collections.emptyList());

        assertFalse(result.isSuccess());
        assertEquals("[Unroutable flow exception occurred.: An exception occurred when trying to compute path:"
                        + " UnroutableFlowException: test exception message]",
                result.getFailReasons().values().toString());
    }

    @Test
    void whenValidPath_responseContainsSuccess() throws UnroutableFlowException {
        when(pathComputer.getPath((AvailableNetwork) any(), any(), anyBoolean()))
                .thenReturn(GetPathsResult
                .builder()
                        .forward(getPath())
                        .reverse(getPath())
                        .build());
        PathComputationValidator pathComputationValidator =
                new PathComputationValidator(availableNetworkFactory, pathComputer);

        GetPathsResult result = pathComputationValidator.validatePath(getFlow(), getPath(), Collections.emptyList());

        assertTrue(result.isSuccess());
    }

    private Path getPath() {
        return Path.builder()
                .srcSwitchId(SWITCH_ID_1)
                .destSwitchId(SWITCH_ID_2)
                .segments(Collections.singletonList(Path.Segment.builder()
                        .srcSwitchId(SWITCH_ID_1)
                        .destSwitchId(SWITCH_ID_2)
                        .srcPort(1)
                        .destPort(2)
                        .build()))
                .build();
    }

    private Flow getFlow() {
        return Flow.builder()
                .flowId("dummy")
                .srcSwitch(Switch.builder().switchId(SWITCH_ID_1).build())
                .srcPort(1)
                .destSwitch(Switch.builder().switchId(SWITCH_ID_2).build())
                .destPort(2)
                .encapsulationType(FlowEncapsulationType.VXLAN)
                .pathComputationStrategy(PathComputationStrategy.COST)
                .build();
    }
}
