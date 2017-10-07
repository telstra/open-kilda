/* Copyright 2017 Telstra Open Source
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

package org.bitbucket.openkilda.topology.config;

import org.neo4j.ogm.session.SessionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import org.springframework.data.neo4j.transaction.Neo4jTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * The Neo4j database configuration.
 */
@Configuration
@EnableTransactionManagement
@ComponentScan("org.bitbucket.openkilda.topology.domain")
@PropertySource("classpath:topology.properties")
@EnableNeo4jRepositories(basePackages = {"org.bitbucket.openkilda.topology.domain.repository"})
public class Neo4jConfig {
    /**
     * The environment variable for username.
     */
    @Value("${security.data.username.env}")
    private String envUsername;

    /**
     * The environment variable for password.
     */
    @Value("${security.data.password.env}")
    private String envPassword;

    /**
     * The service username environment variable name.
     */
    @Value("${security.data.username.default}")
    private String defaultUsername;

    /**
     * The service password environment variable name.
     */
    @Value("${security.data.password.default}")
    private String defaultPassword;

    /**
     * Session factory bean.
     * Constructs a new {@link SessionFactory} by initialising the object-graph mapping meta-data
     * from the given list of domain object packages.
     *
     * @return {@link SessionFactory}
     */
    @Bean
    public SessionFactory sessionFactory() {
        return new SessionFactory(configuration(), "org.bitbucket.openkilda.topology.domain");
    }

    /**
     * Transaction manager bean.
     * Creates a new Neo4jTransactionManager instance.
     * An SessionFactory has to be set to be able to use it.
     *
     * @return {@link Neo4jTransactionManager}
     */
    @Bean
    public Neo4jTransactionManager transactionManager() {
        return new Neo4jTransactionManager(sessionFactory());
    }

    /**
     * Neo4j Configuration bean.
     * Constructs Neo4j configuration with specified driver and uri.
     *
     * @return {@link org.neo4j.ogm.config.Configuration}
     */
    @Bean
    public org.neo4j.ogm.config.Configuration configuration() {
        String username = System.getenv(envUsername);
        if (username == null || username.isEmpty()) {
            username = defaultUsername;
        }
        String password = System.getenv(envPassword);
        if (password == null || password.isEmpty()) {
            password = defaultPassword;
        }

        org.neo4j.ogm.config.Configuration config = new org.neo4j.ogm.config.Configuration();
        config.driverConfiguration()
                .setDriverClassName("org.neo4j.ogm.drivers.http.driver.HttpDriver")
                .setURI(String.format("http://%s:%s@neo4j:7474", username, password));

        return config;
    }
}
