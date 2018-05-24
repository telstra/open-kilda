package org.openkilda.pce.provider;

import org.neo4j.driver.v1.Driver;

import java.io.Serializable;

public interface Auth extends Serializable {
    Driver getDriver();
}
