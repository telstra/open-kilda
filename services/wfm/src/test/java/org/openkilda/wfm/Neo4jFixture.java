package org.openkilda.wfm;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseBuilder;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.kernel.configuration.BoltConnector;

import java.io.File;
import java.nio.file.Path;

public class Neo4jFixture {
    private final String DIRECTORY_NAME = "neo4j";

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

    public GraphDatabaseService getGraphDatabaseService()
    {
        return db;
    }
}
