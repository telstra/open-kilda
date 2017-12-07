package org.openkilda.wfm.isl;

public interface IIslFilter {
    boolean isMatch(DiscoveryNode subject);
}
