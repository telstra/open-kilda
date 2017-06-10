package org.bitbucket.openkilda.topology;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.ogm.session.SessionFactory;
import org.neo4j.server.CommunityBootstrapper;
import org.neo4j.server.NeoServer;
import org.neo4j.server.ServerBootstrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import org.springframework.data.neo4j.transaction.Neo4jTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.io.File;
import java.util.Optional;

/**
 * The Test configuration.
 */
@Configuration
@EnableAutoConfiguration
@EnableTransactionManagement
@ComponentScan({"org.bitbucket.openkilda.topology.domain", "org.bitbucket.openkilda.topology.service"})
@PropertySource("classpath:topology.properties")
@EnableNeo4jRepositories(basePackages = {"org.bitbucket.openkilda.topology.domain.repository"})
public class TestConfig {
    private static final Logger logger = LoggerFactory.getLogger(TestConfig.class);

    /**
     * Session factory bean.
     * Constructs a new {@link SessionFactory} by initialising the object-graph mapping meta-data
     * from the given list of domain object packages.
     *
     * @return {@link SessionFactory}
     */
    @Bean
    public SessionFactory sessionFactory() {
        return new SessionFactory(configuration(), "org.bitbucket.openkilda.topology.domain",
                "org.bitbucket.openkilda.topology.domain.repository", "org.bitbucket.openkilda.topology.service");
    }

    /**
     * Transaction manager bean.
     * Creates a new Neo4jTransactionManager instance.
     * An SessionFactory has to be set to be able to use it.
     *
     * @return {@link }Neo4jTransactionManager}
     */
    @Bean
    public Neo4jTransactionManager transactionManager() {
        return new Neo4jTransactionManager(sessionFactory());
    }

    /*
    @Bean
    public org.neo4j.ogm.config.Configuration configuration() {
        org.neo4j.ogm.config.Configuration config = new org.neo4j.ogm.config.Configuration();
        config.driverConfiguration()
                .setDriverClassName("org.neo4j.ogm.drivers.http.driver.HttpDriver")
                .setURI("http://neo4j:password@localhost:7474");
        return config;
    }
    */

    /**
     * Neo4j Configuration bean.
     * Constructs Neo4j configuration with specified driver and uri.
     *
     * @return {@link org.neo4j.ogm.config.Configuration}
     */
    @Bean
    public org.neo4j.ogm.config.Configuration configuration() {
        org.neo4j.ogm.config.Configuration config = new org.neo4j.ogm.config.Configuration();
        config.driverConfiguration()
                .setDriverClassName("org.neo4j.ogm.drivers.http.driver.HttpDriver")
                .setURI("http://localhost:7474");
        return config;
    }

    /**
     * Neo4j Server bean.
     * Runs Neo4j server for integration tests and returns {@link GraphDatabaseService} instance.
     *
     * @return {@link GraphDatabaseService}
     */
    @Bean(destroyMethod = "shutdown")
    public GraphDatabaseService graphDatabaseService() {
        String homeDir = "./target";
        String configFile = "./src/test/resources/neo4j.conf";
        ServerBootstrapper serverBootstrapper = new CommunityBootstrapper();
        int i = serverBootstrapper.start(new File(homeDir), Optional.of(new File(configFile)));
        switch (i) {
            case ServerBootstrapper.OK:
                logger.debug("Server started");
                break;
            case ServerBootstrapper.GRAPH_DATABASE_STARTUP_ERROR_CODE:
                logger.error("Server failed to start: graph database startup error");
                break;
            case ServerBootstrapper.WEB_SERVER_STARTUP_ERROR_CODE:
                logger.error("Server failed to start: web server startup error");
                break;
            default:
                logger.error("Server failed to start: unknown error");
                break;
        }
        NeoServer neoServer = serverBootstrapper.getServer();
        return neoServer.getDatabase().getGraph();
    }
}
