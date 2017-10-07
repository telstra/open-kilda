/* Copyright 2017 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

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
