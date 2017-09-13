package org.bitbucket.openkilda.messaging;

/**
 * This class contains Kilda-specific Kafka topics.
 */
public enum Topic {
    TEST("kilda-test"),
    HEALTH_CHECK("kilda.health.check");
    /*
    NB_WFM("kilda.nb.wfm"),
    WFM_NB("kilda.wfm.nb"),
    TE_WFM_FLOW("kilda.te.wfm.flow"),
    WFM_TE_FLOW("kilda.wfm.te.flow"),
    WFM_OFS_FLOW("kilda.wfm.ofs.flow"),
    OFS_WFM_FLOW("kilda.ofs.wfm.flow"),
    OFS_WFM_STATS("kilda.ofs.wfm.stats"),
    OFS_WFM_DISCOVERY("kilda.ofs.wfm.discovery"),
    WFM_TE_DISCOVERY("kilda.wfm.te.discovery");
    */

    private String id;

    Topic(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
