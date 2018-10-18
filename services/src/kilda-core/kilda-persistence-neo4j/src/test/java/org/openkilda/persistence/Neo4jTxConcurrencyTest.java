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

package org.openkilda.persistence;

import org.openkilda.model.SwitchId;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.repositories.impl.Neo4jFlowPathRepository;
import org.openkilda.persistence.repositories.impl.Neo4jFlowRepository;
import org.openkilda.persistence.repositories.impl.Neo4jIslRepository;
import org.openkilda.persistence.repositories.impl.Neo4jSwitchRepository;

import org.junit.BeforeClass;

public class Neo4jTxConcurrencyTest extends Neo4jBasedTest {
    static final String TEST_FLOW_1_ID = "test_flow_1";
    static final String TEST_FLOW_2_ID = "test_flow_2";
    static final String TEST_FLOW_3_ID = "test_flow_3";
    static final SwitchId TEST_SWITCH_A_ID = new SwitchId(1);
    static final SwitchId TEST_SWITCH_B_ID = new SwitchId(2);
    static final SwitchId TEST_SWITCH_C_ID = new SwitchId(3);

    static SwitchRepository switchRepository;
    static FlowRepository flowRepository;
    static FlowPathRepository flowSegmentRepository;
    static IslRepository islRepository;

    @BeforeClass
    public static void setUp() {
        switchRepository = new Neo4jSwitchRepository(neo4jSessionFactory, txManager);
        flowRepository = new Neo4jFlowRepository(neo4jSessionFactory, txManager);
        flowSegmentRepository = new Neo4jFlowPathRepository(neo4jSessionFactory, txManager);
        islRepository = new Neo4jIslRepository(neo4jSessionFactory, txManager);
    }
    /*
    @Test
    public void shouldHandleConcurrentRequestsOnSameEntity() throws ExecutionException, InterruptedException {
        // given
        Switch srcSwitch = Switch.builder().switchId(TEST_SWITCH_A_ID).build();
        switchRepository.createOrUpdate(srcSwitch);
        Switch dstSwitch = Switch.builder().switchId(TEST_SWITCH_B_ID).build();
        switchRepository.createOrUpdate(dstSwitch);
        Flow flow = Flow.builder().flowId(TEST_FLOW_1_ID)
                .srcSwitch(srcSwitch).srcPort(1).destSwitch(dstSwitch).destPort(2).build();
        flowRepository.createOrUpdate(flow);

        // when
        ExecutorService executor = Executors.newCachedThreadPool();
        List<Future<Exception>> results = IntStream.range(1, 50).<Callable<Exception>>mapToObj(index -> () -> {
            Instant timestamp = Instant.now().plus(index, ChronoUnit.DAYS);

            try {
                txManager.doInTransaction(() -> {
                    Flow flowToUpdate = flowRepository.findById(TEST_FLOW_1_ID).iterator().next();
                    flowToUpdate.setTimeModify(timestamp);
                    flowRepository.createOrUpdate(flowToUpdate);

                    Flow updated = flowRepository.findById(TEST_FLOW_1_ID).iterator().next();
                    if (!updated.getTimeModify().equals(timestamp)) {
                        throw new RuntimeException("Failed to update timeModify: " + updated);
                    }
                });
            } catch (Exception ex) {
                return ex;
            }

            return null;
        }).map(executor::submit).collect(Collectors.toList());

        // then
        for (Future<Exception> future : results) {
            assertNull(future.get());
        }
    }

    @Test
    public void shouldHandleConcurrentRequestsOnIntersectEntities() throws ExecutionException, InterruptedException {
        // given
        Switch srcSwitch = Switch.builder().switchId(TEST_SWITCH_A_ID).build();
        switchRepository.createOrUpdate(srcSwitch);
        Switch dstSwitch = Switch.builder().switchId(TEST_SWITCH_B_ID).build();
        switchRepository.createOrUpdate(dstSwitch);
        Switch dst2Switch = Switch.builder().switchId(TEST_SWITCH_C_ID).build();
        switchRepository.createOrUpdate(dst2Switch);
        Flow flow1 = Flow.builder().flowId(TEST_FLOW_1_ID)
                .srcSwitch(srcSwitch).srcPort(1).destSwitch(dstSwitch).destPort(2).build();
        flowRepository.createOrUpdate(flow1);
        Flow flow2 = Flow.builder().flowId(TEST_FLOW_2_ID)
                .srcSwitch(dstSwitch).srcPort(1).destSwitch(dst2Switch).destPort(2).build();
        flowRepository.createOrUpdate(flow2);
        Flow flow3 = Flow.builder().flowId(TEST_FLOW_3_ID)
                .srcSwitch(dst2Switch).srcPort(1).destSwitch(srcSwitch).destPort(2).build();
        flowRepository.createOrUpdate(flow3);

        // when
        ExecutorService executor = Executors.newCachedThreadPool();
        List<Future<Exception>> results = IntStream.range(1, 50).<Callable<Exception>>mapToObj(index -> () -> {
            String flowId = Arrays.asList(TEST_FLOW_1_ID, TEST_FLOW_2_ID, TEST_FLOW_3_ID).get(index % 3);
            Instant timestamp = Instant.now().plus(index, ChronoUnit.DAYS);

            try {
                txManager.doInTransaction(() -> {
                    Flow flowToUpdate = flowRepository.findById(flowId).iterator().next();
                    flowToUpdate.setTimeModify(timestamp);
                    flowRepository.createOrUpdate(flowToUpdate);

                    Flow updated = flowRepository.findById(flowId).iterator().next();
                    if (!updated.getTimeModify().equals(timestamp)) {
                        throw new RuntimeException("Failed to update timeModify: " + updated);
                    }
                });
            } catch (Exception ex) {
                return ex;
            }

            return null;
        }).map(executor::submit).collect(Collectors.toList());

        // then
        for (Future<Exception> future : results) {
            assertNull(future.get());
        }
    }

    @Test
    public void shouldHandleConcurrentRequestsOnPairedEntities() throws ExecutionException, InterruptedException {
        // given
        Switch srcSwitch = Switch.builder().switchId(TEST_SWITCH_A_ID).build();
        switchRepository.createOrUpdate(srcSwitch);
        Switch dstSwitch = Switch.builder().switchId(TEST_SWITCH_B_ID).build();
        switchRepository.createOrUpdate(dstSwitch);
        Switch dst2Switch = Switch.builder().switchId(TEST_SWITCH_C_ID).build();
        switchRepository.createOrUpdate(dst2Switch);

        Flow flow1 = Flow.builder().flowId(TEST_FLOW_1_ID).cookie(Flow.FORWARD_FLOW_COOKIE_MASK | 1L)
                .srcSwitch(srcSwitch).srcPort(1).destSwitch(dstSwitch).destPort(2).build();
        Flow reverseFlow1 = Flow.builder().flowId(TEST_FLOW_1_ID).cookie(Flow.REVERSE_FLOW_COOKIE_MASK | 1L)
                .srcSwitch(dstSwitch).srcPort(2).destSwitch(srcSwitch).destPort(1).build();
        flowRepository.createOrUpdate(FlowPair.builder().forward(flow1).reverse(reverseFlow1).build());

        Flow flow2 = Flow.builder().flowId(TEST_FLOW_2_ID).cookie(Flow.FORWARD_FLOW_COOKIE_MASK | 2L)
                .srcSwitch(dstSwitch).srcPort(1).destSwitch(dst2Switch).destPort(2).build();
        Flow reverseFlow2 = Flow.builder().flowId(TEST_FLOW_2_ID).cookie(Flow.REVERSE_FLOW_COOKIE_MASK | 2L)
                .srcSwitch(dst2Switch).srcPort(2).destSwitch(dstSwitch).destPort(1).build();
        flowRepository.createOrUpdate(FlowPair.builder().forward(flow2).reverse(reverseFlow2).build());

        Flow flow3 = Flow.builder().flowId(TEST_FLOW_3_ID).cookie(Flow.FORWARD_FLOW_COOKIE_MASK | 3L)
                .srcSwitch(dst2Switch).srcPort(1).destSwitch(srcSwitch).destPort(2).build();
        Flow reverseFlow3 = Flow.builder().flowId(TEST_FLOW_3_ID).cookie(Flow.REVERSE_FLOW_COOKIE_MASK | 3L)
                .srcSwitch(srcSwitch).srcPort(2).destSwitch(dst2Switch).destPort(1).build();
        flowRepository.createOrUpdate(FlowPair.builder().forward(flow3).reverse(reverseFlow3).build());

        // when
        ExecutorService executor = Executors.newCachedThreadPool();
        List<Future<Exception>> results = IntStream.range(1, 50).<Callable<Exception>>mapToObj(index -> () -> {
            String flowId = Arrays.asList(TEST_FLOW_1_ID, TEST_FLOW_2_ID, TEST_FLOW_3_ID).get(index % 3);
            Instant timestamp = Instant.now().plus(index, ChronoUnit.DAYS);

            try {
                txManager.doInTransaction(() -> {
                    FlowPair flowToUpdate = flowRepository.findFlowPairById(flowId).get();
                    flowToUpdate.getForward().setTimeModify(timestamp);
                    flowToUpdate.getReverse().setTimeModify(timestamp);
                    flowRepository.createOrUpdate(flowToUpdate);

                    FlowPair updated = flowRepository.findFlowPairById(flowId).get();
                    if (!updated.getForward().getTimeModify().equals(timestamp)
                            || !updated.getReverse().getTimeModify().equals(timestamp)) {
                        throw new RuntimeException("Failed to update timeModify: " + updated);
                    }
                });
            } catch (Exception ex) {
                return ex;
            }

            return null;
        }).map(executor::submit).collect(Collectors.toList());

        // then
        for (Future<Exception> future : results) {
            assertNull(future.get());
        }
    }

    @Test
    public void shouldHandleConcurrentRequestsOnAdjacentEntities() throws ExecutionException, InterruptedException {
        // given
        Switch srcSwitch = Switch.builder().switchId(TEST_SWITCH_A_ID).build();
        switchRepository.createOrUpdate(srcSwitch);
        Switch dstSwitch = Switch.builder().switchId(TEST_SWITCH_B_ID).build();
        switchRepository.createOrUpdate(dstSwitch);

        Flow flow1 = Flow.builder().flowId(TEST_FLOW_1_ID).cookie(Flow.FORWARD_FLOW_COOKIE_MASK | 1L)
                .srcSwitch(srcSwitch).srcPort(1).destSwitch(dstSwitch).destPort(2).build();
        Flow reverseFlow1 = Flow.builder().flowId(TEST_FLOW_1_ID).cookie(Flow.REVERSE_FLOW_COOKIE_MASK | 1L)
                .srcSwitch(dstSwitch).srcPort(2).destSwitch(srcSwitch).destPort(1).build();
        flowRepository.createOrUpdate(FlowPair.builder().forward(flow1).reverse(reverseFlow1).build());

        Flow flow2 = Flow.builder().flowId(TEST_FLOW_2_ID).cookie(Flow.FORWARD_FLOW_COOKIE_MASK | 2L)
                .srcSwitch(dstSwitch).srcPort(1).destSwitch(srcSwitch).destPort(2).build();
        Flow reverseFlow2 = Flow.builder().flowId(TEST_FLOW_2_ID).cookie(Flow.REVERSE_FLOW_COOKIE_MASK | 2L)
                .srcSwitch(srcSwitch).srcPort(2).destSwitch(dstSwitch).destPort(1).build();
        flowRepository.createOrUpdate(FlowPair.builder().forward(flow2).reverse(reverseFlow2).build());

        FlowSegment flowSegment1 = FlowSegment.builder().flowId(TEST_FLOW_3_ID)
                .cookie(Flow.FORWARD_FLOW_COOKIE_MASK | 3L)
                .srcSwitch(srcSwitch).srcPort(1).destSwitch(dstSwitch).destPort(2).build();
        flowSegmentRepository.createOrUpdate(flowSegment1);

        FlowSegment flowSegment2 = FlowSegment.builder().flowId(TEST_FLOW_3_ID)
                .cookie(Flow.REVERSE_FLOW_COOKIE_MASK | 3L)
                .srcSwitch(dstSwitch).srcPort(2).destSwitch(srcSwitch).destPort(1).build();
        flowSegmentRepository.createOrUpdate(flowSegment2);

        // when
        Random random = new Random();
        ExecutorService executor = Executors.newCachedThreadPool();
        List<Future<Exception>> results = IntStream.range(1, 50).<Callable<Exception>>mapToObj(index -> () -> {
            try {
                txManager.doInTransaction(() -> {
                    if (index % 3 == 0) {
                        String flowId = Arrays.asList(TEST_FLOW_1_ID, TEST_FLOW_2_ID).get(index % 2);
                        FlowPair flowToUpdate = flowRepository.findFlowPairById(flowId).get();
                        Instant timestamp = Instant.now().plus(index, ChronoUnit.DAYS);
                        flowToUpdate.getForward().setTimeModify(timestamp);
                        flowToUpdate.getReverse().setTimeModify(timestamp);
                        flowRepository.createOrUpdate(flowToUpdate);

                        FlowPair updated = flowRepository.findFlowPairById(flowId).get();
                        if (!updated.getForward().getTimeModify().equals(timestamp)
                                || !updated.getReverse().getTimeModify().equals(timestamp)) {
                            throw new RuntimeException("Failed to update timeModify: " + updated);
                        }

                    } else {
                        long cookie = Arrays.asList(Flow.FORWARD_FLOW_COOKIE_MASK, Flow.REVERSE_FLOW_COOKIE_MASK)
                                .get(index % 2) | 3L;
                        FlowSegment segmentToUpdate =
                                flowSegmentRepository.findByFlowIdAndCookie(TEST_FLOW_3_ID, cookie).iterator().next();
                        long latency = Math.abs(random.nextLong());
                        segmentToUpdate.setLatency(latency);
                        flowSegmentRepository.createOrUpdate(segmentToUpdate);

                        FlowSegment updated = flowSegmentRepository.findByFlowIdAndCookie(TEST_FLOW_3_ID, cookie)
                                .iterator().next();
                        if (updated.getLatency() != latency) {
                            throw new RuntimeException("Failed to update latency: " + updated);
                        }
                    }
                });
            } catch (Exception ex) {
                return ex;
            }

            return null;
        }).map(executor::submit).collect(Collectors.toList());

        // then
        for (Future<Exception> future : results) {
            assertNull(future.get());
        }
    }

    @Test
    public void shouldHandleConcurrentCreationOnAdjacentEntities() throws ExecutionException, InterruptedException {
        // given
        Switch srcSwitch = Switch.builder().switchId(TEST_SWITCH_A_ID).build();
        switchRepository.createOrUpdate(srcSwitch);
        Switch dstSwitch = Switch.builder().switchId(TEST_SWITCH_B_ID).build();
        switchRepository.createOrUpdate(dstSwitch);

        // when
        Random random = new Random();
        ExecutorService executor = Executors.newCachedThreadPool();
        List<Future<Exception>> results = IntStream.range(1, 50).<Callable<Exception>>mapToObj(index -> () -> {
            int srcPort = index * 2;
            int destPort = srcPort + 1;

            try {
                txManager.doInTransaction(() -> {
                    Isl isl = Isl.builder().srcSwitch(srcSwitch).srcPort(srcPort)
                            .destSwitch(dstSwitch).destPort(destPort).build();
                    islRepository.createOrUpdate(isl);
                });
            } catch (Exception ex) {
                return ex;
            }

            int latency = Math.abs(random.nextInt());

            try {
                txManager.doInTransaction(() -> {
                    Isl islToUpdate = islRepository.findByEndpoints(srcSwitch.getSwitchId(), srcPort,
                            dstSwitch.getSwitchId(), destPort).get();
                    islToUpdate.setLatency(latency);
                    islRepository.createOrUpdate(islToUpdate);
                });
            } catch (Exception ex) {
                return ex;
            }

            try {
                Isl updated = islRepository.findByEndpoints(srcSwitch.getSwitchId(), srcPort,
                        dstSwitch.getSwitchId(), destPort).get();
                if (updated.getLatency() != latency) {
                    throw new RuntimeException("Failed to update latency: " + updated);
                }
                return null;
            } catch (Exception ex) {
                return ex;
            }
        }).map(executor::submit).collect(Collectors.toList());

        // then
        for (Future<Exception> future : results) {
            assertNull(future.get());
        }
    }
    */
}

