/* Copyright 2018 Telstra Open Source
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

package org.openkilda.persistence.neo4j;

import org.neo4j.ogm.session.Session;

class Neo4jSessionHolder {
    static final Neo4jSessionHolder INSTANCE = new Neo4jSessionHolder();

    private final ThreadLocal<Session> sessionHolder = new ThreadLocal<>();

    private Neo4jSessionHolder() {
    }

    Session getSession() {
        return sessionHolder.get();
    }

    void setSession(Session session) {
        sessionHolder.set(session);
    }

    boolean hasSession() {
        return sessionHolder.get() != null;
    }

    void clean() {
        sessionHolder.remove();
    }
}
