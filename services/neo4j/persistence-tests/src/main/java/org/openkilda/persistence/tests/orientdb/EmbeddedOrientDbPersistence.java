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

import org.openkilda.model.SwitchId;
import org.openkilda.persistence.ferma.OrientDbConfig;
import org.openkilda.persistence.ferma.OrientDbPersistenceManager;

import com.orientechnologies.orient.core.db.ODatabaseDocumentInternal;
import com.orientechnologies.orient.core.db.ODatabaseSession;
import com.orientechnologies.orient.core.db.ODatabaseType;
import com.orientechnologies.orient.core.db.OrientDB;
import com.orientechnologies.orient.core.db.OrientDBConfig;
import com.orientechnologies.orient.graph.graphml.OGraphMLReader;
import com.orientechnologies.orient.server.OServer;
import com.orientechnologies.orient.server.OServerMain;
import com.orientechnologies.orient.server.config.OServerConfiguration;
import com.orientechnologies.orient.server.config.OServerNetworkConfiguration;
import com.orientechnologies.orient.server.config.OServerUserConfiguration;
import com.tinkerpop.blueprints.impls.orient.OrientGraph;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

@Slf4j
public class EmbeddedOrientDbPersistence implements AutoCloseable {
    static final String ORIENT_DB_PATH = "memory:./databases/";
    static final String ORIENT_DB_NAME = "test";

    public final OServer server;
    public final OrientDB orientDb;

    public EmbeddedOrientDbPersistence() throws Exception {
        Logger.getLogger("").setLevel(Level.SEVERE);

        OServerConfiguration cfg = new OServerConfiguration();
        cfg.network = new OServerNetworkConfiguration(cfg);
        cfg.users = new OServerUserConfiguration[]{
                new OServerUserConfiguration("root", "root", "*")};

        server = OServerMain.create();
        server.startup(cfg).activate();

        orientDb = new OrientDB(ORIENT_DB_PATH, null, null,
                OrientDBConfig.defaultConfig());

        if (orientDb.createIfNotExists(ORIENT_DB_NAME, ODatabaseType.MEMORY)) {
            try (ODatabaseSession session = orientDb.open(ORIENT_DB_NAME, "admin", "admin")) {
                loadTestData(session);
            }
        }

        if (!createPersistenceManager().getRepositoryFactory().createSwitchRepository().exists(new SwitchId(2))) {
            throw new IllegalStateException("Failed to load to OrientDB");
        }
    }

    public OrientDbPersistenceManager createPersistenceManager() {
        return new OrientDbPersistenceManager(new OrientDbConfig() {
            @Override
            public String getDbPath() {
                return ORIENT_DB_PATH;
            }

            @Override
            public String getDbName() {
                return ORIENT_DB_NAME;
            }

            @Override
            public String getDbType() {
                return ODatabaseType.MEMORY.name();
            }

            @Override
            public String getUser() {
                return "admin";
            }

            @Override
            public String getPassword() {
                return "admin";
            }
        }, orientDb);
    }

    @Override
    public void close() {
        orientDb.drop(ORIENT_DB_NAME);
        orientDb.close();
        server.shutdown();
    }

    private void loadTestData(ODatabaseSession databaseSession) {
        importFile(databaseSession, "/org/openkilda/persistence/tests/orientdb/data.graphml");
    }

    private void importFile(ODatabaseSession databaseSession, String file) {
        try (InputStream is = getClass().getResourceAsStream(file)) {
            new OGraphMLReader(new OrientGraph((ODatabaseDocumentInternal) databaseSession)).inputGraph(is);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read a file: " + file, e);
        }
    }
}
