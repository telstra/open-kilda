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

package org.openkilda.persistence.ferma.repositories;

import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;

import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.PathSegmentRepository;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class FermaPathSegmentRepositoryTest extends InMemoryGraphBasedTest {
    static final PathId TEST_PATH_ID = new PathId("test_path");

    PathSegmentRepository pathSegmentRepository;
    FlowPathRepository flowPathRepository;

    @Before
    public void setUp() {
        pathSegmentRepository = repositoryFactory.createPathSegmentRepository();
        flowPathRepository = repositoryFactory.createFlowPathRepository();
    }

    @Test
    public void shouldFindSegmentsByPath() {
        FlowPath path = createFlowPath(TEST_PATH_ID);
        assertThat(flowPathRepository.findAll(), hasSize(1));

        List<PathSegment> foundSegments = pathSegmentRepository.findByPathId(TEST_PATH_ID);
        assertThat(foundSegments, containsInAnyOrder(path.getSegments().toArray()));
    }

    private FlowPath createFlowPath(PathId pathId) {
        Switch srcSwitch = createTestSwitch(1);
        Switch dstSwitch = createTestSwitch(2);
        Switch intSwitch = createTestSwitch(3);

        FlowPath flowPath = FlowPath.builder()
                .pathId(pathId)
                .srcSwitch(srcSwitch)
                .destSwitch(dstSwitch)
                .status(FlowPathStatus.ACTIVE)
                .build();
        PathSegment segment1 = PathSegment.builder()
                .pathId(pathId)
                .srcSwitch(srcSwitch)
                .srcPort(1)
                .destSwitch(intSwitch)
                .destPort(3)
                .build();
        PathSegment segment2 = PathSegment.builder()
                .pathId(pathId)
                .srcSwitch(intSwitch)
                .srcPort(4)
                .destSwitch(dstSwitch)
                .destPort(2)
                .build();
        flowPath.setSegments(asList(segment1, segment2));
        flowPathRepository.add(flowPath);
        return flowPath;
    }
}
