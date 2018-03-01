package org.openkilda.pce.provider;

import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.GraphDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthNeo4j implements Auth {
    private static final Logger logger = LoggerFactory.getLogger(AuthNeo4j.class);

    private final String host;
    private final String login;
    private final String password;

    public AuthNeo4j(String host, String login, String password) {
        this.host = host;
        this.login = login;
        this.password = password;
    }

    @Override
    public NeoDriver connect() {
        String address = String.format("bolt://%s", host);

        logger.info("NEO4J connect {} (login=\"{}\", password=\"*****\")", address, login);
        return new NeoDriver(GraphDatabase.driver(address, AuthTokens.basic(login, password)));
    }
}
