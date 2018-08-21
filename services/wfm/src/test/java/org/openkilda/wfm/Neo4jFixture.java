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

package org.openkilda.wfm;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseBuilder;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.kernel.configuration.BoltConnector;

import java.io.File;
import java.nio.file.Path;

public class Neo4jFixture {
    private static final String DIRECTORY_NAME = "neo4j";

    private final GraphDatabaseBuilder dbBuilder;
    private GraphDatabaseService db = null;
    private final String listenAddress;

    public Neo4jFixture(Path rootDir, String listenAddress) {
        this.listenAddress = listenAddress;

        Path dbPath = rootDir.resolve(DIRECTORY_NAME);
        File dbDir = dbPath.toFile();
        dbDir.mkdir();

        BoltConnector bolt = new BoltConnector("0");
        dbBuilder = new GraphDatabaseFactory()
                .newEmbeddedDatabaseBuilder(dbDir)
                .setConfig(bolt.type, "BOLT")
                .setConfig(bolt.enabled, "true")
                .setConfig(bolt.listen_address, this.listenAddress);
    }

    public void start() {
        db = dbBuilder.newGraphDatabase();
    }

    public void stop() {
        db.shutdown();
    }

    public String getListenAddress() {
        return listenAddress;
    }

    public GraphDatabaseService getGraphDatabaseService() {
        return db;
    }
}
