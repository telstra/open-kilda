package org.openkilda.pce.provider;

import java.io.Serializable;

public interface Auth extends Serializable {
    PathComputer connect();
}
