package org.openkilda.atdd.staging.service.neo4j;

import org.neo4j.driver.v1.Driver;

public interface Neo4jService {
    Driver getDriver();
}
