/* Copyright 2019 Telstra Open Source
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

package org.openkilda.persistence.tests.orientdb;

import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.ferma.OrientDbPersistenceManager;
import org.openkilda.persistence.ferma.model.Flow;
import org.openkilda.persistence.ferma.repositories.FermaRepositoryFactory;
import org.openkilda.persistence.ferma.repositories.FlowRepository;
import org.openkilda.persistence.ferma.repositories.IslRepository;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public class OrientDbShortestPathBenchmark {

    @State(Scope.Benchmark)
    public static class SharedOrientDdPersistence {
        EmbeddedOrientDbPersistence persistence;

        @Setup
        public void setUp() throws Exception {
            persistence = new EmbeddedOrientDbPersistence();
        }

        @TearDown
        public void tearDown() {
            persistence.close();
        }
    }

    @State(Scope.Thread)
    public static class OrientDbPersistenceResources {
        OrientDbPersistenceManager persistenceManager;
        TransactionManager transactionManager;
        FermaRepositoryFactory repositoryFactory;
        List<String> flowIds;

        @Setup(Level.Iteration)
        public void setUp(SharedOrientDdPersistence sharedOrientDbPersistence) {
            persistenceManager = sharedOrientDbPersistence.persistence.createPersistenceManager();
            transactionManager = persistenceManager.getTransactionManager();
            repositoryFactory = persistenceManager.getRepositoryFactory();

            IslRepository islRepository = repositoryFactory.createIslRepository();
            //TODO: init Orient with network structures
        }

        @TearDown(Level.Iteration)
        public void tearDown() {
            persistenceManager.close();
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Fork(1)
    @Warmup(iterations = 1)
    @Measurement(iterations = 10)
    public void shortestPathBenchmark(OrientDbPersistenceResources persistence, Blackhole blackhole) {
        persistence.transactionManager.doInTransaction(() -> {
            //TODO: traverse for shortestPath
            /*
                g.V().hasLabel("switch").has("switch_id", "1").
                emit().
                repeat(both("isl").simplePath().
                until(hasLabel("switch").has("switch_id", "2").or().loops().is(eq(30))).
                hasLabel("switch").has("switch_id", "2").
                order().by(path().count(local), incr).
                limit(1).
                path().unfold()
             */
            FlowRepository flowRepository = persistence.repositoryFactory.createFlowRepository();
            IntStream.range(0, 9).mapToObj(i -> persistence.flowIds.get(i))
                    .forEach(flowId -> {
                        Flow flow = flowRepository.findById(flowId)
                                .orElseThrow(() -> new IllegalStateException("Unable to find a flow"));
                        blackhole.consume(flow);
                        blackhole.consume(flow.getFlowId());
                    });
        });
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(OrientDbShortestPathBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
