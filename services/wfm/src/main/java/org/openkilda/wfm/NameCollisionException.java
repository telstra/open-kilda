package org.openkilda.wfm;

public class NameCollisionException extends Exception {
    public NameCollisionException() {
        this("Topology component's ID collision");
    }

    NameCollisionException(String s) {
        super(s);
    }
}
