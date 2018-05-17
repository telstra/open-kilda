package org.openkilda.pce.provider;

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.GraphDatabase;

@ToString
@Slf4j
public class AuthNeo4j implements Auth {
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

        log.info("NEO4J connect {} (login=\"{}\", password=\"*****\")", address, login);
        return new NeoDriver(GraphDatabase.driver(address, AuthTokens.basic(login, password)));
    }
}
