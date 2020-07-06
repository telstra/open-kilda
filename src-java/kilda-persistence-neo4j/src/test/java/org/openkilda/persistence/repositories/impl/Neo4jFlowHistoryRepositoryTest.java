/* Copyright 2020 Telstra Open Source
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

package org.openkilda.persistence.repositories.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.openkilda.model.history.FlowHistory;
import org.openkilda.persistence.Neo4jBasedTest;
import org.openkilda.persistence.repositories.history.FlowHistoryRepository;

import org.junit.BeforeClass;
import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Neo4jFlowHistoryRepositoryTest extends Neo4jBasedTest {

    private static final String TASK_1 = "TASK_1";
    private static final String ACTION_1 = "ACTION_1";
    private static final String ACTION_2 = "ACTION_1";
    private static final String ACTION_3 = "ACTION_1";
    private static final Instant TIME_1 = Instant.parse("2020-07-06T11:04:41Z");
    private static final Instant TIME_2 = Instant.parse("2020-07-06T11:04:41.482Z");
    private static final Instant TIME_3 = Instant.parse("2020-07-06T11:04:42.321Z");

    private static FlowHistoryRepository repository;

    @BeforeClass
    public static void setUp() {
        repository = new Neo4jFlowHistoryRepository(neo4jSessionFactory, txManager);
    }

    @Test
    public void findByTaskIdOrderTest() {
        List<FlowHistory> expected = new ArrayList<>();
        expected.add(getFlowHistory(TASK_1, ACTION_1, ACTION_1, TIME_1));
        expected.add(getFlowHistory(TASK_1, ACTION_2, ACTION_2, TIME_2));
        expected.add(getFlowHistory(TASK_1, ACTION_3, ACTION_3, TIME_3));

        for (FlowHistory flowHistory : expected) {
            repository.createOrUpdate(flowHistory);
        }

        List<FlowHistory> actual = new ArrayList<>(repository.findByTaskId(TASK_1));
        assertEquals(expected, actual);
        // result must be sorted by time
        assertTrue(actual.get(0).getTimestamp().isBefore(actual.get(1).getTimestamp()));
        assertTrue(actual.get(1).getTimestamp().isBefore(actual.get(2).getTimestamp()));
    }

    private FlowHistory getFlowHistory(String taskId, String action, String details, Instant timestamp) {
        return FlowHistory.builder()
                .taskId(taskId)
                .action(action)
                .details(details)
                .timestamp(timestamp)
                .build();
    }
}
