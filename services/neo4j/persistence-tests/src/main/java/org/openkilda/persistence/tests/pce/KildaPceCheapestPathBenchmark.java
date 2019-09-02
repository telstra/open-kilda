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

package org.openkilda.persistence.tests.pce;

import org.openkilda.config.provider.PropertiesBasedConfigurationProvider;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.AvailableNetworkFactory;
import org.openkilda.pce.Path;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.PathComputerConfig;
import org.openkilda.pce.PathComputerFactory;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.tests.neo4j.EmbeddedNeo4jOgmPersistence;
import org.openkilda.persistence.tests.pce.TopologyForPceBuilder.Island;
import org.openkilda.persistence.tests.pce.TopologyForPceBuilder.Region;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class KildaPceCheapestPathBenchmark {
    @State(Scope.Benchmark)
    public static class SharedNeo4jPersistence {
        EmbeddedNeo4jOgmPersistence persistence;

        @Setup
        public void setUp() {
            persistence = new EmbeddedNeo4jOgmPersistence(false);
        }

        @TearDown
        public void tearDown() {
            persistence.close();
        }
    }

    @State(Scope.Thread)
    public static class Neo4jPersistenceResources {
        PersistenceManager persistenceManager;
        TransactionManager transactionManager;
        RepositoryFactory repositoryFactory;

        @Setup(Level.Iteration)
        public void setUp(SharedNeo4jPersistence sharedNeo4jPersistence) {
            persistenceManager = sharedNeo4jPersistence.persistence.createPersistenceManager();
            transactionManager = persistenceManager.getTransactionManager();
            repositoryFactory = persistenceManager.getRepositoryFactory();
        }

        @TearDown(Level.Iteration)
        public void tearDown() {
            persistenceManager.close();
        }
    }

    @State(Scope.Thread)
    public static class PathComputerPersistence {
        PathComputer pathComputer;

        @Setup(Level.Iteration)
        public void setUp(Neo4jPersistenceResources neo4jPersistenceResources) {
            PropertiesBasedConfigurationProvider configurationProvider = new PropertiesBasedConfigurationProvider();
            PathComputerConfig pathComputerConfig = configurationProvider.getConfiguration(PathComputerConfig.class);
            AvailableNetworkFactory availableNetworkFactory =
                    new AvailableNetworkFactory(pathComputerConfig, neo4jPersistenceResources.repositoryFactory);
            PathComputerFactory pathComputerFactory =
                    new PathComputerFactory(pathComputerConfig, availableNetworkFactory);
            pathComputer = pathComputerFactory.getPathComputer();
        }
    }

    @State(Scope.Benchmark)
    public static class SmallCircleTopologyResources {
        @Param({"3", "5", "10"})
        protected int topologySize;

        @Param({"50", "150", "300"})
        protected int maxHops;

        protected List<Island> islands;

        @Setup
        public void setUp(SharedNeo4jPersistence sharedNeo4jPersistence) {
            try (PersistenceManager persistenceManager = sharedNeo4jPersistence.persistence.createPersistenceManager()) {
                islands = persistenceManager.getTransactionManager().doInTransaction(() -> {
                    TopologyForPceBuilder topologyBuilder =
                            new TopologyForPceBuilder(persistenceManager.getRepositoryFactory(), 10, topologySize, 30);
                    return topologyBuilder.buildCircles();
                });
            }
        }
    }

    @State(Scope.Benchmark)
    public static class MediumCircleTopologyResources {
        @Param({"10"})
        protected int topologySize;

        @Param({"300"})
        protected int maxHops;

        protected List<Island> islands;

        @Setup
        public void setUp(SharedNeo4jPersistence sharedNeo4jPersistence) {
            try (PersistenceManager persistenceManager = sharedNeo4jPersistence.persistence.createPersistenceManager()) {
                islands = persistenceManager.getTransactionManager().doInTransaction(() -> {
                    TopologyForPceBuilder topologyBuilder =
                            new TopologyForPceBuilder(persistenceManager.getRepositoryFactory(), 10, topologySize, 30);
                    return topologyBuilder.buildCircles();
                });
            }
        }
    }

    @State(Scope.Benchmark)
    public static class CircleTopologyResources {
        @Param({"3", "10"})
        protected int topologySize;

        @Param({"50", "300", "1000"})
        protected int maxHops;

        protected List<Island> islands;

        @Setup
        public void setUp(SharedNeo4jPersistence sharedNeo4jPersistence) {
            try (PersistenceManager persistenceManager = sharedNeo4jPersistence.persistence.createPersistenceManager()) {
                islands = persistenceManager.getTransactionManager().doInTransaction(() -> {
                    TopologyForPceBuilder topologyBuilder =
                            new TopologyForPceBuilder(persistenceManager.getRepositoryFactory(), 10, topologySize, 30);
                    return topologyBuilder.buildCircles();
                });
            }
        }
    }

    @State(Scope.Benchmark)
    public static class MeshTopologyResources {
        @Param({"3", "10", "30"})
        protected int topologySize;

        @Param({"50", "300", "1000"})
        protected int maxHops;

        protected List<Island> islands;

        @Setup
        public void setUp(SharedNeo4jPersistence sharedNeo4jPersistence) {
            try (PersistenceManager persistenceManager = sharedNeo4jPersistence.persistence.createPersistenceManager()) {
                islands = persistenceManager.getTransactionManager().doInTransaction(() -> {
                    TopologyForPceBuilder topologyBuilder =
                            new TopologyForPceBuilder(persistenceManager.getRepositoryFactory(), 10, topologySize, 30);
                    return topologyBuilder.buildMeshes();
                });
            }
        }
    }

    @State(Scope.Benchmark)
    public static class TreeTopologyResources {
        @Param({"3", "10", "30"})
        protected int topologySize;

        @Param({"50", "300", "1000"})
        protected int maxHops;

        protected List<Island> islands;

        @Setup
        public void setUp(SharedNeo4jPersistence sharedNeo4jPersistence) {
            try (PersistenceManager persistenceManager = sharedNeo4jPersistence.persistence.createPersistenceManager()) {
                islands = persistenceManager.getTransactionManager().doInTransaction(() -> {
                    TopologyForPceBuilder topologyBuilder =
                            new TopologyForPceBuilder(persistenceManager.getRepositoryFactory(), 10, topologySize, 30);
                    return topologyBuilder.buildTree(5);
                });
            }
        }
    }

    @State(Scope.Benchmark)
    public static class StarTopologyResources {
        @Param({"3", "10", "30"})
        protected int topologySize;

        @Param({"50", "300", "1000"})
        protected int maxHops;

        protected List<Island> islands;

        @Setup
        public void setUp(SharedNeo4jPersistence sharedNeo4jPersistence) {
            try (PersistenceManager persistenceManager = sharedNeo4jPersistence.persistence.createPersistenceManager()) {
                islands = persistenceManager.getTransactionManager().doInTransaction(() -> {
                    TopologyForPceBuilder topologyBuilder =
                            new TopologyForPceBuilder(persistenceManager.getRepositoryFactory(), 10, topologySize, 30);
                    return topologyBuilder.buildStars(5);
                });
            }
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Fork(1)
    @Warmup(iterations = 1)
    @Measurement(iterations = 10)
    @Threads(5)
    public void concurrent5CheapestPathOnCircleTopologyBenchmark(PathComputerPersistence pathComputerPersistence,
                                                                 MediumCircleTopologyResources topologyResources, Blackhole blackhole)
            throws RecoverableException {
            Island island = topologyResources.islands.get(0);
            Region startRegion = island.getRegions().get(0);
            Region endRegion = island.getRegions().get(island.getRegions().size() / 2);

            cheapestPath(pathComputerPersistence,
                    startRegion.getSwitches().get(0).getSwitchId(),
                    endRegion.getSwitches().get(endRegion.getSwitches().size() / 2).getSwitchId(),
                    topologyResources.maxHops,
                    blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Fork(1)
    @Warmup(iterations = 1)
    @Measurement(iterations = 10)
    @Threads(30)
    public void concurrent30CheapestPathOnCircleTopologyBenchmark(PathComputerPersistence pathComputerPersistence,
                                                                 MediumCircleTopologyResources topologyResources, Blackhole blackhole)
            throws RecoverableException {
        Island island = topologyResources.islands.get(0);
        Region startRegion = island.getRegions().get(0);
        Region endRegion = island.getRegions().get(island.getRegions().size() / 2);

        cheapestPath(pathComputerPersistence,
                startRegion.getSwitches().get(0).getSwitchId(),
                endRegion.getSwitches().get(endRegion.getSwitches().size() / 2).getSwitchId(),
                topologyResources.maxHops,
                blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Fork(1)
    @Warmup(iterations = 1)
    @Measurement(iterations = 10)
    @Threads(50)
    public void concurrent50CheapestPathOnCircleTopologyBenchmark(PathComputerPersistence pathComputerPersistence,
                                                                  MediumCircleTopologyResources topologyResources, Blackhole blackhole)
            throws RecoverableException {
        Island island = topologyResources.islands.get(0);
        Region startRegion = island.getRegions().get(0);
        Region endRegion = island.getRegions().get(island.getRegions().size() / 2);

        cheapestPath(pathComputerPersistence,
                startRegion.getSwitches().get(0).getSwitchId(),
                endRegion.getSwitches().get(endRegion.getSwitches().size() / 2).getSwitchId(),
                topologyResources.maxHops,
                blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Fork(1)
    @Warmup(iterations = 1)
    @Measurement(iterations = 10)
    public void cheapestPathOnCircleTopologyBenchmark(PathComputerPersistence pathComputerPersistence,
                                                      CircleTopologyResources topologyResources, Blackhole blackhole) throws RecoverableException {
        Island island = topologyResources.islands.get(0);
        Region startRegion = island.getRegions().get(0);
        Region endRegion = island.getRegions().get(island.getRegions().size() / 2);

        cheapestPath(pathComputerPersistence,
                startRegion.getSwitches().get(0).getSwitchId(),
                endRegion.getSwitches().get(endRegion.getSwitches().size() / 2).getSwitchId(),
                topologyResources.maxHops,
                blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Fork(1)
    @Warmup(iterations = 1)
    @Measurement(iterations = 10)
    public void cheapestPathOnMeshTopologyBenchmark(PathComputerPersistence pathComputerPersistence,
                                                    MeshTopologyResources topologyResources, Blackhole blackhole) throws RecoverableException {
        Island island = topologyResources.islands.get(0);
        Region startRegion = island.getRegions().get(0);
        Region endRegion = island.getRegions().get(island.getRegions().size() / 2);

        cheapestPath(pathComputerPersistence,
                startRegion.getSwitches().get(0).getSwitchId(),
                endRegion.getSwitches().get(endRegion.getSwitches().size() / 2).getSwitchId(),
                topologyResources.maxHops,
                blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Fork(1)
    @Warmup(iterations = 1)
    @Measurement(iterations = 10)
    public void cheapestPathOnTreeTopologyBenchmark(PathComputerPersistence pathComputerPersistence,
                                                    TreeTopologyResources topologyResources, Blackhole blackhole) throws RecoverableException {
        Island island = topologyResources.islands.get(0);
        Region startRegion = island.getRegions().get(0);
        Region endRegion = island.getRegions().get(island.getRegions().size() / 2);

        cheapestPath(pathComputerPersistence,
                startRegion.getSwitches().get(0).getSwitchId(),
                endRegion.getSwitches().get(endRegion.getSwitches().size() / 2).getSwitchId(),
                topologyResources.maxHops,
                blackhole);
    }


    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Fork(1)
    @Warmup(iterations = 1)
    @Measurement(iterations = 10)
    public void cheapestPathOnStarTopologyBenchmark(PathComputerPersistence pathComputerPersistence,
                                                    StarTopologyResources topologyResources, Blackhole blackhole) throws RecoverableException {
        Island island = topologyResources.islands.get(0);
        Region startRegion = island.getRegions().get(0);
        Region endRegion = island.getRegions().get(island.getRegions().size() / 2);

        cheapestPath(pathComputerPersistence,
                startRegion.getSwitches().get(0).getSwitchId(),
                endRegion.getSwitches().get(endRegion.getSwitches().size() / 2).getSwitchId(),
                topologyResources.maxHops,
                blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Fork(1)
    @Warmup(iterations = 1)
    @Measurement(iterations = 10)
    public void crossIslandCheapestPathBenchmark(PathComputerPersistence pathComputerPersistence,
                                                 SmallCircleTopologyResources topologyResources, Blackhole blackhole) throws RecoverableException {
        Region startRegion = topologyResources.islands.get(0).getRegions().get(0);
        Region endRegion = topologyResources.islands.get(1).getRegions().get(0);

        try {
            List<Path> paths = pathComputerPersistence.pathComputer.getNPaths(
                    startRegion.getSwitches().get(0).getSwitchId(),
                    endRegion.getSwitches().get(0).getSwitchId(), 10, FlowEncapsulationType.TRANSIT_VLAN);
            if (!paths.isEmpty() && !paths.get(0).getSegments().isEmpty()) {
                throw new IllegalStateException("Found unexpected path: " + paths.get(0));
            }
            blackhole.consume(paths);
        } catch (UnroutableFlowException ex) {
            System.out.println("Path was not found (as expected): " + ex.toString());
        }
    }

    private void cheapestPath(PathComputerPersistence pathComputerPersistence, SwitchId startSwitch, SwitchId endSwitch,
                              int maxHops, Blackhole blackhole) throws RecoverableException {
        try {
            List<Path> paths = pathComputerPersistence.pathComputer.getNPaths(startSwitch, endSwitch, 10, FlowEncapsulationType.TRANSIT_VLAN);
            if (paths.isEmpty() || paths.get(0).getSegments().isEmpty()) {
                System.out.println("Expected path was not found");
            }
            blackhole.consume(paths);
        } catch (UnroutableFlowException ex) {
            System.out.println("Expected path was not found: " + ex.toString());
        }
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(KildaPceCheapestPathBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
