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

package org.openkilda.northbound.converter;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import org.openkilda.messaging.info.network.Path;
import org.openkilda.messaging.info.network.PathValidationResult;
import org.openkilda.messaging.model.FlowPathDto;
import org.openkilda.messaging.model.FlowPathDto.FlowProtectedPathDto;
import org.openkilda.messaging.payload.flow.GroupFlowPathPayload;
import org.openkilda.messaging.payload.flow.GroupFlowPathPayload.FlowProtectedPathsPayload;
import org.openkilda.messaging.payload.flow.PathNodePayload;
import org.openkilda.messaging.payload.network.PathDto;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.dto.v2.flows.PathValidateResponse;

import org.junit.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

public class PathMapperTest {
    public static final int NANOS = 12_345_678;
    public static final long BANDWIDTH = 100_000L;
    private final PathMapper pathDtoOnlyMapper = new PathMapper() {
        @Override
        public GroupFlowPathPayload mapGroupFlowPathPayload(FlowPathDto data) {
            return null;
        }

        @Override
        public FlowProtectedPathsPayload mapFlowProtectedPathPayload(FlowProtectedPathDto data) {
            return null;
        }

        @Override
        public PathValidateResponse toPathValidateResponse(PathValidationResult data) {
            return null;
        }
    };

    @Test
    public void whenNoProtectedPath_convertToPathDtoTest() {
        PathDto pathDto = pathDtoOnlyMapper.mapToPath(getPath(getNodes()));

        assertThat(pathDto.getIsBackupPath()).isEqualTo(false);
        assertThat(pathDto.getProtectedPath()).isNull();
        assertThat(pathDto.getBandwidth()).isEqualTo(BANDWIDTH);
        assertThat(pathDto.getNodes()).isEqualTo(getNodes());
        assertThat(pathDto.getLatency()).isEqualTo(Duration.ofNanos(NANOS).toNanos());
        assertThat(pathDto.getLatencyNs()).isEqualTo(Duration.ofNanos(NANOS).toNanos());
        assertThat(pathDto.getLatencyMs()).isEqualTo(Duration.ofNanos(NANOS).toMillis());
    }

    @Test
    public void whenPathWithProtectedPath_convertToPathDtoTest() {
        PathDto pathDto = pathDtoOnlyMapper.mapToPath(getPathWithProtectedPath(getPath(getNodes()), getNodes()));

        assertThat(pathDto.getIsBackupPath()).isEqualTo(false);
        assertThat(pathDto.getProtectedPath()).isNotNull();
        assertThat(pathDto.getBandwidth()).isEqualTo(BANDWIDTH);
        assertThat(pathDto.getNodes()).isEqualTo(getNodes());
        assertThat(pathDto.getLatency()).isEqualTo(Duration.ofNanos(NANOS).toNanos());
        assertThat(pathDto.getLatencyNs()).isEqualTo(Duration.ofNanos(NANOS).toNanos());
        assertThat(pathDto.getLatencyMs()).isEqualTo(Duration.ofNanos(NANOS).toMillis());

        assertThat(pathDto.getProtectedPath().getIsBackupPath()).isEqualTo(false);
        assertThat(pathDto.getProtectedPath().getProtectedPath()).isNull();
        assertThat(pathDto.getProtectedPath().getBandwidth()).isEqualTo(BANDWIDTH);
        assertThat(pathDto.getProtectedPath().getNodes()).isEqualTo(getNodes());
        assertThat(pathDto.getProtectedPath().getLatency()).isEqualTo(Duration.ofNanos(NANOS).toNanos());
        assertThat(pathDto.getProtectedPath().getLatencyNs()).isEqualTo(Duration.ofNanos(NANOS).toNanos());
        assertThat(pathDto.getProtectedPath().getLatencyMs()).isEqualTo(Duration.ofNanos(NANOS).toMillis());
    }

    @Test
    public void whenProtectedPathWithProtectedPath_errorIsRaisedTest() {
        assertThatThrownBy(() -> pathDtoOnlyMapper.mapToPath(getPathWithProtectedPath(
                getPathWithProtectedPath(getPath(getNodes()), getNodes()), getNodes())))
                .isInstanceOf(IllegalStateException.class);
    }

    private Path getPath(List<PathNodePayload> nodes) {
        return getPathWithProtectedPath(null, nodes);
    }

    private Path getPathWithProtectedPath(Path protectedPath, List<PathNodePayload> nodes) {
        return Path.builder()
                .protectedPath(protectedPath)
                .latency(Duration.ofNanos(NANOS))
                .bandwidth(BANDWIDTH)
                .isBackupPath(false)
                .nodes(nodes)
                .build();
    }

    private List<PathNodePayload> getNodes() {
        return Arrays.asList(
                PathNodePayload.builder()
                        .inputPort(1)
                        .outputPort(2)
                        .switchId(new SwitchId("00:01"))
                        .build(),
                PathNodePayload.builder()
                        .inputPort(2)
                        .outputPort(3)
                        .switchId(new SwitchId("00:02"))
                        .build());
    }
}
