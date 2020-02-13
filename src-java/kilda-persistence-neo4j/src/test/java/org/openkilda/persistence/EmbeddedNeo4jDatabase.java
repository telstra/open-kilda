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

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseBuilder;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseFactoryState;
import org.neo4j.kernel.configuration.BoltConnector;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.impl.factory.CommunityEditionModule;
import org.neo4j.kernel.impl.factory.DatabaseInfo;
import org.neo4j.kernel.impl.factory.GraphDatabaseFacadeFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * A wrapper for {@link GraphDatabaseBuilder} that builds an embedded instance of {@link GraphDatabaseService}.
 *
 * @see GraphDatabaseFactory#newEmbeddedDatabase
 */
public class EmbeddedNeo4jDatabase {
    private static final String DEFAULT_LISTEN_ADDRESS = "localhost:27600";
    private static final String CONTENT_DIRECTORY_NAME = "neo4j";

    private final String listenAddress;
    private final File contentDirectory;
    private final GraphDatabaseService dbInstance;

    public EmbeddedNeo4jDatabase(File baseFolder) {
        this(baseFolder, DEFAULT_LISTEN_ADDRESS);
    }

    public EmbeddedNeo4jDatabase(File baseFolder, String listenAddress) {
        this.listenAddress = listenAddress;

        contentDirectory = new File(baseFolder, CONTENT_DIRECTORY_NAME);
        if (!contentDirectory.mkdir()) {
            throw new IllegalStateException("The content directory for Neo4j already exists.");
        }

        BoltConnector bolt = new BoltConnector("0");

        Map<String, String> config = new HashMap<>();
        config.put(bolt.type.name(), "BOLT");
        config.put(bolt.enabled.name(), "true");
        config.put(bolt.listen_address.name(), this.listenAddress);
        config.put(GraphDatabaseFacadeFactory.Configuration.ephemeral.name(), "true");

        dbInstance = new GraphDatabaseFacadeFactory(DatabaseInfo.COMMUNITY, CommunityEditionModule::new)
                .newFacade(contentDirectory, Config.defaults(config),
                        new GraphDatabaseFactoryState().databaseDependencies());

        Runtime.getRuntime().addShutdownHook(new Thread(dbInstance::shutdown));
    }

    /**
     * Stops the instance and clean up the content directory.
     */
    public void stop() {
        dbInstance.shutdown();

        contentDirectory.delete();
    }

    public String getConnectionUri() {
        return "bolt://" + listenAddress;
    }
}
