package org.openkilda.wfm.topology.stats;

import org.neo4j.driver.v1.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class CypherExecutor {
    private static final Logger logger = LoggerFactory.getLogger(CypherExecutor.class);
    private final Driver driver;

    public CypherExecutor(String url) {
        this(url, null, null);
    }

    public CypherExecutor(String url, String username, String password) {
        boolean hasPassword = password != null && !password.isEmpty();
        AuthToken token = hasPassword ? AuthTokens.basic(username, password) : AuthTokens.none();
        logger.debug("connecting to neo4j at " + url);
        driver = GraphDatabase.driver(url, token,
                Config.build().withEncryptionLevel(Config.EncryptionLevel.NONE).toConfig());
    }

    public Iterator<Map<String, Object>> query(String query, Map<String, Object> params) {
        try (Session session = driver.session()) {
            List<Map<String, Object>> list = session.run(query, params)
                    .list(r -> r.asMap(CypherExecutor::convert));
            return list.iterator();
        }
    }

    static Object convert(Value value) {
        switch (value.type().name()) {
            case "PATH":
                return value.asList(CypherExecutor::convert);
            case "NODE":
            case "RELATIONSHIP":
                return value.asMap();
        }
        return value.asObject();
    }
}
