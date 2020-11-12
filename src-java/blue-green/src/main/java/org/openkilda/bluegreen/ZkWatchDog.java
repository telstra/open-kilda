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

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.AsyncCallback.DataCallback;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

@Slf4j
public class ZkWatchDog extends ZkClient implements WatchDog, DataCallback {

    private static final String SIGNAL = "signal";
    private static final String BUILD_VERSION = "build-version";

    private String signalPath;
    private Signal signal;

    private String buildVersionPath;
    private String buildVersion = "v3r$i0n";

    private Set<LifeCycleObserver> observers = new HashSet<>();
    private Set<BuildVersionObserver> buildVersionObservers = new HashSet<>();

    @Builder
    public ZkWatchDog(String id, String serviceName, int apiVersion, String connectionString,
                      int sessionTimeout, Signal signal) throws IOException {
        super(id, serviceName, apiVersion, connectionString, sessionTimeout);

        this.buildVersionPath = getPaths(serviceName, id, BUILD_VERSION);
        this.signalPath = getPaths(serviceName, id, SIGNAL);
        if (signal == null) {
            signal = Signal.NONE;
        }
        this.signal = signal;
        initWatch();
    }

    @Override
    void validateNodes() throws KeeperException, InterruptedException {
        super.validateNodes();
        if (zookeeper.exists(signalPath, false) == null) {
            zookeeper.create(signalPath, signal.toString().getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }
        if (zookeeper.exists(buildVersionPath, false) == null) {
            zookeeper.create(buildVersionPath, buildVersion.getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }
    }


    private void checkData(String path) throws KeeperException, InterruptedException {
        zookeeper.getData(path, this, this, null);
    }

    private void checkSignal() throws KeeperException, InterruptedException {
        checkData(signalPath);
    }

    private void checkBuildVersion() throws KeeperException, InterruptedException {
        checkData(buildVersionPath);
    }

    @Override
    void initWatch() {
        try {
            validateNodes();
            checkSignal();
            checkBuildVersion();
        } catch (KeeperException e) {
            log.error(e.getMessage());
        } catch (InterruptedException e) {
            log.error(e.getMessage());
        }
    }


    @Override
    public void subscribe(LifeCycleObserver observer) {
        observers.add(observer);
    }

    @Override
    public void subscribe(BuildVersionObserver observer) {
        buildVersionObservers.add(observer);
    }

    @Override
    public void unsubscribe(LifeCycleObserver observer) {
        observers.remove(observer);
    }

    @Override
    public void unsubscribe(BuildVersionObserver observer) {
        buildVersionObservers.remove(observer);
    }

    @Override
    public void process(WatchedEvent event) {
        System.out.println(event);
        log.info("Received event: %s", event);
        try {
            if (!refreshConnection(event.getState())) {
                if (signalPath.equals(event.getPath())) {
                    checkSignal();
                }
                if (buildVersionPath.equals(event.getPath())) {
                    checkBuildVersion();
                }
            }
        } catch (IOException e) {
            log.error("Failed to read zk event: %s", e.getMessage());
            e.printStackTrace();
        } catch (InterruptedException e) {
            log.error("Zk event interrupted: %s", e.getMessage());
            e.printStackTrace();
        } catch (KeeperException e) {
            log.error("Zk keeper error: %s", e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void processResult(int rc, String path, Object ctx, byte[] data, Stat stat) {
        log.debug("Received result on path");
        if (signalPath.equals(path) && data != null && data.length > 0) {
            String signalString = new String(data);
            try {
                signal = Signal.valueOf(signalString);
                notifyObservers();
            } catch (Exception e) {
                log.error("Received unknown signal: %s", signalString);
            }
        }

        if (buildVersionPath.equals(path) && data != null && data.length > 0) {
            this.buildVersion = new String(data);
            notifyBuildVersionObservers();
        }
    }

    protected void notifyObservers() {
        for (LifeCycleObserver observer : observers) {
            observer.handle(signal);
        }
    }

    protected void notifyBuildVersionObservers() {
        for (BuildVersionObserver observer : buildVersionObservers) {
            observer.handle(buildVersion);
        }
    }
}



