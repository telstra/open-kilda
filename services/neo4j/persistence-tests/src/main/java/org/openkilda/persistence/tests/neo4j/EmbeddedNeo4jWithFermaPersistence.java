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

package org.openkilda.persistence.tests.neo4j;

import static java.lang.String.format;

import org.openkilda.model.SwitchId;
import org.openkilda.persistence.ferma.Neo4jWithFermaPersistenceManager;

import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.neo4j.tinkerpop.api.Neo4jFactory;
import org.neo4j.tinkerpop.api.Neo4jGraphAPI;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Slf4j
public class EmbeddedNeo4jWithFermaPersistence implements AutoCloseable {
    public final Neo4jGraphAPI graphApi;

    public EmbeddedNeo4jWithFermaPersistence(boolean loadTestData) throws IOException {
        Logger.getLogger("").setLevel(Level.SEVERE);

        Map<String, String> config = ImmutableMap.of("apoc.import.file.enabled", "true");

        graphApi = Neo4jFactory.Builder.open(Files.createTempDirectory("neo4j").toAbsolutePath().toString(), config);

        runQueriesInFile("/org/openkilda/persistence/tests/neo4j/schema");

        if (loadTestData) {
            loadTestData();

            try (Neo4jWithFermaPersistenceManager persistenceManager = createPersistenceManager()) {
                if (!persistenceManager.getRepositoryFactory().createSwitchRepository().exists(new SwitchId(2))) {
                    throw new IllegalStateException("Failed to load to Neo4j");
                }
            }
        }
    }

    public Neo4jWithFermaPersistenceManager createPersistenceManager() {
        return new Neo4jWithFermaPersistenceManager(graphApi);
    }

    @Override
    public void close() {
        graphApi.shutdown();
    }

    private void loadTestData() {
        runCypherFiles(IntStream.range(0, 22)
                .mapToObj(i -> format("/org/openkilda/persistence/tests/neo4j/data-%02d", i)));
    }

    private void runQueriesInFile(String file) {
        try (InputStream is = getClass().getResourceAsStream(file)) {
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = br.readLine()) != null) {
                graphApi.execute(line, Collections.emptyMap());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read a file: " + file, e);
        }
    }

    private void runCypherFiles(Stream<String> files) {
        String fileList = files.map(file -> {
            URL url = getClass().getResource(file);
            File tmpCopy;
            try {
                tmpCopy = File.createTempFile("persistence-tests-", ".cypher");
                FileUtils.copyURLToFile(url, tmpCopy);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to read data file: " + file, e);
            }
            return format("'%s'", tmpCopy.getAbsoluteFile().toURI());
        }).collect(Collectors.joining(","));

        graphApi.execute(format("CALL apoc.cypher.runFiles([%s])", fileList), Collections.emptyMap());
    }
}
