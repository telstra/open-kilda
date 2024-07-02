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

package org.openkilda.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.openkilda.config.ApplicationProperties;
import org.openkilda.constants.Direction;
import org.openkilda.exception.InvalidRequestException;
import org.openkilda.integration.service.StatsIntegrationService;
import org.openkilda.integration.service.SwitchIntegrationService;
import org.openkilda.integration.source.store.SwitchInventoryService;
import org.openkilda.model.victoria.MetricValues;
import org.openkilda.model.victoria.RangeQueryParams;
import org.openkilda.model.victoria.Status;
import org.openkilda.model.victoria.VictoriaData;
import org.openkilda.model.victoria.dbdto.VictoriaDbData;
import org.openkilda.model.victoria.dbdto.VictoriaDbRes;
import org.openkilda.store.service.StoreService;
import org.openkilda.test.MockitoExtension;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    private StatsService statsService;
    private StatsIntegrationService statsIntegrationService;

    @BeforeEach
    void setUp() {
        // Create a mock for the StatsIntegrationService
        statsIntegrationService = mock(StatsIntegrationService.class);
        SwitchIntegrationService switchIntegrationService = mock(SwitchIntegrationService.class);
        StoreService storeService = mock(StoreService.class);
        SwitchInventoryService switchInventoryService = mock(SwitchInventoryService.class);

        ApplicationProperties applicationProperties = mock(ApplicationProperties.class);
        when(applicationProperties.getMetricPrefix()).thenReturn("kilda.");

        // Initialize the service to be tested with the mock dependency
        statsService = new StatsService(statsIntegrationService, switchIntegrationService,
                storeService, switchInventoryService, applicationProperties);
    }

    @Test
    void getTransformedFlowVictoriaStats() throws InvalidRequestException {
        VictoriaDbRes mockDbRes = new VictoriaDbRes();
        mockDbRes.setStatus(Status.SUCCESS);
        MetricValues metricValues = new MetricValues();
        Map<String, String> tags = new HashMap<>();
        tags.put("direction", "forward");
        tags.put("flowid", "sub_2");

        metricValues.setTags(tags);
        List<String[]> stringArrays = IntStream.range(1, 11)
                .mapToObj(i -> new String[]{String.valueOf(i), String.valueOf(i + 999)})
                .collect(Collectors.toList());
        metricValues.setValues(stringArrays);
        VictoriaDbData victoriaDbData = new VictoriaDbData();
        victoriaDbData.setResult(Collections.singletonList(metricValues));
        mockDbRes.setData(victoriaDbData);

        when(statsIntegrationService.getVictoriaStats(any(RangeQueryParams.class))).thenReturn(mockDbRes);

        // Call the method being tested
        List<VictoriaData> result = statsService.getTransformedFlowVictoriaStats(
                "flow", "2023-08-28-14:14:34", "2023-08-28-14:14:50", "30s", "sub_2",
                Collections.singletonList("packets"), Direction.FORWARD);

        verify(statsIntegrationService).getVictoriaStats(RangeQueryParams.builder()
                .start(1693232074L).end(1693232090L).step("30s")
                .query("rate(sum(kilda.flow.packets{flowid='sub_2', direction='forward'}) by (flowid,direction))")
                .build());
        // ... verify that the method returned the expected result based on the mockDbRes
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("kilda.flow.packets", result.get(0).getMetric());
        assertNotNull(result.get(0).getTimeToValueMap());
        assertEquals(10, result.get(0).getTimeToValueMap().size());
        assertEquals(Status.SUCCESS, result.get(0).getStatus());
        assertEquals(2, result.get(0).getTags().size());
    }

    @Test
    void getTransformedFlowVictoriaStatsWrongParams() {
        Throwable e = assertThrows(InvalidRequestException.class, () -> statsService.getTransformedFlowVictoriaStats(
                null, "2023-08-28-14:14:34", "2023-08-28-14:14:50", "30s", "sub_2",
                Collections.singletonList("packets"), Direction.FORWARD));
        assertEquals("This statsType is unsupported", e.getMessage());

        e = assertThrows(InvalidRequestException.class, () -> statsService.getTransformedFlowVictoriaStats(
                "flow", "", "2023-08-28-14:14:50", "30s", "sub_2",
                Collections.singletonList("packets"), Direction.FORWARD));
        assertEquals("startDate, metric, and flowid must not be null or empty", e.getMessage());

        e = assertThrows(InvalidRequestException.class, () -> statsService.getTransformedFlowVictoriaStats(
                "flow", "2023-08-28-14:14:34", "asdasd", "30s", "sub_2",
                Collections.singletonList("packets"), Direction.FORWARD));
        assertEquals("Date wrong format, should be: 'yyyy-MM-dd-HH:mm:ss' or empty", e.getMessage());

        e = assertThrows(InvalidRequestException.class, () -> statsService.getTransformedFlowVictoriaStats(
                "flow", "2023-08-28-14:14:34", "2023-08-28-14:14:50", "30s", "",
                Collections.singletonList("packets"), Direction.FORWARD));
        assertEquals("startDate, metric, and flowid must not be null or empty", e.getMessage());

        e = assertThrows(InvalidRequestException.class, () -> statsService.getTransformedFlowVictoriaStats(
                "flow", "2023-08-28-14:14:34", "2023-08-28-14:14:50", "30s", "sub_2",
                Collections.singletonList(""), Direction.FORWARD));
        assertEquals("There is no such metric: ", e.getMessage());

        e = assertThrows(InvalidRequestException.class, () -> statsService.getTransformedFlowVictoriaStats(
                "flow", "2023-08-28-14:14:34", "2023-08-28-14:14:50", "30s", "sub_2",
                Collections.emptyList(), Direction.FORWARD));
        assertEquals("startDate, metric, and flowid must not be null or empty", e.getMessage());
    }
}
