/* Copyright 2020 Telstra Open Source
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

package org.openkilda.bluegreen;

import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;

import java.io.IOException;
import java.nio.file.Paths;

@SuperBuilder
@Slf4j
public abstract class ZkClient implements Watcher {
    private static final String ROOT = "/";
    private static final int DEFAULT_SESSION_TIMEOUT = 30000;

    protected String id;
    protected int apiVersion;
    protected String serviceName;
    protected ZooKeeper zookeeper;
    private final String connectionString;
    private final int sessionTimeout;

    public ZkClient(String id, String serviceName, int apiVersion, String connectionString,
                    int sessionTimeout) throws IOException {
        if (sessionTimeout == 0) {
            sessionTimeout = DEFAULT_SESSION_TIMEOUT;
        }
        this.apiVersion = apiVersion;
        this.serviceName = serviceName;
        this.id = id;
        this.zookeeper = new ZooKeeper(connectionString, sessionTimeout, this);
        this.connectionString = connectionString;
        this.sessionTimeout = sessionTimeout;
    }

    void initWatch() {

    }

    String getPaths(String... paths) {
        return Paths.get(ROOT, paths).toString();
    }

    boolean refreshConnection(KeeperState state) throws IOException {
        if (state == KeeperState.Disconnected || state == KeeperState.Expired) {
            zookeeper = new ZooKeeper(connectionString, sessionTimeout, this);
            initWatch();
            return true;
        }
        return false;
    }

    void validateNodes() throws KeeperException, InterruptedException {
        if (zookeeper.exists(getPaths(serviceName), false) == null) {
            try {
                zookeeper.create(getPaths(serviceName), "".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            } catch (Exception e) {
                // pass
            }
        }
        if (zookeeper.exists(getPaths(serviceName), false) == null) {
            log.error("Zk node %s still does not exists", getPaths(serviceName));
        }
        if (zookeeper.exists(getPaths(serviceName, id), false) == null) {
            try {
                zookeeper.create(getPaths(serviceName, id), Integer.toString(apiVersion).getBytes(),
                        Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            } catch (Exception e) {
                // pass
            }
        }
        if (zookeeper.exists(getPaths(serviceName, id), false) == null) {
            log.error("Zk node %s still does not exists", getPaths(serviceName, id));
        }
    }

}


