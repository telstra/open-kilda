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
import com.tinkerpop.blueprints.impls.orient.OrientGraph;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

@Slf4j
public class RemoteOrientDbPersistence implements OrientDbPersistence, AutoCloseable {
    static final String ORIENT_DB_NAME = "test";

    private final String url;
    private final String user;
    private final String password;
    public final OrientDB orientDb;

    public RemoteOrientDbPersistence(String url, String user, String password, boolean loadTestData) {
        Logger.getLogger("").setLevel(Level.SEVERE);

        this.url = url;
        this.user = user;
        this.password = password;

        System.out.println("open orientdb");

        orientDb = new OrientDB(url, user, password, OrientDBConfig.defaultConfig());
        if (orientDb.createIfNotExists(ORIENT_DB_NAME, ODatabaseType.PLOCAL)) {
            System.out.println("create a " + ORIENT_DB_NAME);
            try (ODatabaseSession session = orientDb.open(ORIENT_DB_NAME, "admin", "admin")) {
                session.execute("sql", "CREATE CLASS isl EXTENDS E");
                session.execute("sql", "CREATE CLASS switch EXTENDS V");
                session.execute("sql", "CREATE VERTEX switch");
                session.execute("sql", "CREATE PROPERTY switch.name STRING");
                session.execute("sql", "CREATE INDEX switchId ON switch (name) UNIQUE");

                if (loadTestData) {
                    loadTestData(session);
                }
            }
        }

        if (loadTestData) {
            try (OrientDbPersistenceManager persistenceManager = createPersistenceManager()) {
                if (!persistenceManager.getRepositoryFactory().createSwitchRepository().exists(new SwitchId(2))) {
                    throw new IllegalStateException("Failed to load to OrientDB");
                }
            }
        } else {
            try (ODatabaseSession session = orientDb.open(ORIENT_DB_NAME, "admin", "admin")) {
                session.execute("sql", "TRUNCATE CLASS isl UNSAFE");
                session.execute("sql", "TRUNCATE CLASS switch UNSAFE");
            }
        }
    }

    @Override
    public OrientDbPersistenceManager createPersistenceManager() {
        return new OrientDbPersistenceManager(new OrientDbConfig() {
            @Override
            public String getDbPath() {
                return url;
            }

            @Override
            public String getDbName() {
                return ORIENT_DB_NAME;
            }

            @Override
            public String getDbType() {
                return ODatabaseType.PLOCAL.name();
            }

            @Override
            public String getUser() {
                return user;
            }

            @Override
            public String getPassword() {
                return password;
            }
        }, orientDb);
    }

    @Override
    public void close() {
        System.out.println("closing orientdb");
        //orientDb.drop(ORIENT_DB_NAME);
        orientDb.close();
    }

    private void loadTestData(ODatabaseSession databaseSession) {
        importFile(databaseSession, "/org/openkilda/persistence/tests/orientdb/data.graphml");
    }

    private void importFile(ODatabaseSession databaseSession, String file) {
        try (InputStream is = getClass().getResourceAsStream(file)) {
            OrientGraph graph = new OrientGraph((ODatabaseDocumentInternal) databaseSession);
            new OGraphMLReader(graph).inputGraph(is);
            graph.setAutoStartTx(false);
            graph.commit();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read a file: " + file, e);
        }
    }
}
