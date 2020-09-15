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

package org.openkilda.snmp.collector.metrics;

import org.openkilda.messaging.snmp.metric.SnmpMetricData;
import org.openkilda.messaging.snmp.metric.SnmpMetricPoint;
import org.openkilda.snmp.collector.collection.SnmpConfigManager;
import org.openkilda.snmp.collector.collection.data.SnmpHost;
import org.openkilda.snmp.collector.collection.data.SnmpMetricEntry;
import org.openkilda.snmp.collector.collection.data.SnmpMetricGroup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.IpAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.SMIConstants;
import org.snmp4j.smi.Variable;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.DefaultPDUFactory;
import org.snmp4j.util.TreeEvent;
import org.snmp4j.util.TreeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.PostConstruct;

/**
 * SnmpMetricCollector execute SNMP queries to collect certain metrics from a set of hosts.
 * The set of metrics for a host is based on its sysObjectId and configuration files
 * The set of hosts to query is provided by external entities, such as topology part in a network controller.
 */
@Component
public class SnmpMetricCollector {

    private static final Logger LOG = LoggerFactory.getLogger(SnmpMetricCollector.class);
    private static final String SYS_OBJECT_ID_OID = "1.3.6.1.2.1.1.2.0";

    @Autowired
    private BlockingQueue<SnmpMetricData> metricQueue;

    @Value("${snmp.version:v2c}")
    String version;
    @Value("${snmp.community:public}")
    String community;
    @Value(value = "${snmp.retries:2}")
    int retries;
    @Value("${snmp.timeout_ms:5000}")
    int timeout;
    @Value("${snmp.query_interval:300}")
    private int interval;
    @Value("${snmp.collector_pool_size:5}")
    private int collectorPoolSize;

    @Autowired
    private SnmpConfigManager configManager;
    private final Lock hostLock = new ReentrantLock();
    Set<SnmpHost> hosts = new HashSet<>();
    Snmp snmp;

    Set<Integer> supportedDataTypes = new HashSet<>();

    // cache sysObjectOid to Metrics, avoid repeatedly query a host for that. An ExpiryCache is better
    private final Map<String, String> hostNameToSysIdCache = new ConcurrentHashMap<>();

    private ExecutorService executor;
    private Timer timer;


    /**
     * Init constants and properties.
     */
    @PostConstruct
    public void init() {
        supportedDataTypes.addAll(Arrays.asList(
                SMIConstants.SYNTAX_COUNTER32,
                SMIConstants.SYNTAX_COUNTER64,
                SMIConstants.SYNTAX_INTEGER,
                SMIConstants.SYNTAX_INTEGER32,
                SMIConstants.SYNTAX_UNSIGNED_INTEGER32,
                SMIConstants.SYNTAX_GAUGE32,
                SMIConstants.SYNTAX_TIMETICKS
        ));

        executor = Executors.newFixedThreadPool(collectorPoolSize);
        timer = new Timer("snmp-query-scheduler", true);
    }


    /**
     * Start the SNMP metric collection process.
     */
    public void start() throws IOException {
        // start snmp
        TransportMapping<? extends Address> transport = new DefaultUdpTransportMapping();
        snmp = new Snmp(transport);
        transport.listen();
        LOG.info("Started SNMP listener.");

        // add the hosts in the work queue
        BlockingQueue<SnmpHost> workQueue = new LinkedBlockingDeque<>();
        workQueue.addAll(hosts);

        // kick start collecting tasks
        executor = Executors.newFixedThreadPool(collectorPoolSize, new ThreadFactory() {
            private final AtomicInteger threadIdx = new AtomicInteger();

            @Override
            public Thread newThread(final Runnable r) {
                final Thread t = new Thread(r, "snmp-collector-" + threadIdx.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        });

        for (int i = 0; i < collectorPoolSize; i++) {
            Runnable task = new Runnable() {
                @Override
                public void run() {
                    try {
                        while (true) {
                            SnmpHost host = workQueue.take();
                            collectMetrics(host);
                        }
                    } catch (InterruptedException e) {
                        LOG.warn("SNMP metric collection task interrupted.", e);
                    } catch (Exception e) {
                        LOG.warn("SNMP collector encountered exception.", e);
                    }
                }
            };
            executor.submit(task);
        }
        LOG.info("Started {} SNMP metric collector tasks", collectorPoolSize);

        // schedule a timer task; periodically put the hosts back into the queue.
        // hosts need to be protected.
        TimerTask enqueueHostsTask = new TimerTask() {
            @Override
            public void run() {
                hostLock.lock();
                try {
                    workQueue.addAll(hosts);
                } finally {
                    hostLock.unlock();
                }
            }
        };
        timer.scheduleAtFixedRate(enqueueHostsTask, 0, TimeUnit.MILLISECONDS.convert(interval, TimeUnit.SECONDS));
        LOG.info("Started timer task to do metric collection at interval {}s.", interval);
    }

    /**
     * stop the thread started by this collector.
     */
    public void stop() {
        timer.cancel();
        executor.shutdownNow();
    }

    /**
     * Add provided list of hosts to SNMP metric collection.
     *
     * @param hosts the list of hosts to be added
     */
    public void addHosts(List<SnmpHost> hosts) {
        hostLock.lock();
        try {
            this.hosts.addAll(hosts);
        } finally {
            hostLock.unlock();
        }
    }

    /**
     * Remove a list of hosts from SNMP metric collection.
     *
     * @param hosts the list of hosts to be removed
     */
    public void removeHosts(List<SnmpHost> hosts) {
        hostLock.lock();
        try {
            this.hosts.removeAll(hosts);
        } finally {
            hostLock.unlock();
        }

        // clean the cache to prevent stale data
        hosts.stream().forEach(x -> hostNameToSysIdCache.remove(x.getName()));
    }

    /**
     * Clear the list of hosts for SNMP collection. This will effectively stop the collection process.
     */
    public void clearHosts() {
        hostLock.lock();
        try {
            this.hosts.clear();
        } finally {
            hostLock.unlock();
        }

        // clean the cache to prevent stale data
        hostNameToSysIdCache.clear();
    }

    /**
     * Return the current list of hosts participating for SNMP metric collection.
     *
     * @return the current list of hosts
     */
    public List<SnmpHost> getHosts() {
        hostLock.lock();
        try {
            List<SnmpHost> copy = new LinkedList<>();
            copy.addAll(this.hosts);
            return copy;
        } finally {
            hostLock.unlock();
        }
    }

    /**
     * Given a host, first get the host's sysObjectId;
     * from this sysObjectId resolve all related metric OIDs and using bulkget to query.
     */
    private void collectMetrics(SnmpHost host) {

        String sysObjectID = hostNameToSysIdCache.get(host.getName());
        if (sysObjectID == null) {

            Variable sysObjectIdVar = queryScalarMetric(host, SYS_OBJECT_ID_OID);
            if (sysObjectIdVar == null) {
                LOG.warn("Skipped SNMP metric collection for host {} as no valid sysObjectID returned", host.getName());
                return;
            }

            sysObjectID = sysObjectIdVar.toString();
            hostNameToSysIdCache.put(host.getName(), sysObjectID);
            LOG.info("Caching SNMP host {} sysObjectId {}", host.getName(), sysObjectID);
        }
        LOG.debug("SNMP host {} has sysObjectID={}", host.getName(), sysObjectID);

        // now based on this oid, we need to resolve all the relevant metrics and query them.
        Collection<SnmpMetricGroup> metricGroups = configManager.getMatchedMetricGroups(sysObjectID);
        for (SnmpMetricGroup metricGroup : metricGroups) {
            for (SnmpMetricEntry entry : metricGroup.getMetrics()) {
                Map<String, Variable> collectedMetrics = bulkQuery(host, entry);
                publishMetrics(host, entry, collectedMetrics);
            }
        }

    }

    /**
     * Using SNMP BulkGet to collect a metric against a host.
     *
     * @param host a host/device
     * @param metricEntry a SnmpMetricPoint, it may be scalar, and also may be a column
     * @return Mapping between index(like ifIndex) and the variable
     */
    private Map<String, Variable> bulkQuery(SnmpHost host, SnmpMetricEntry metricEntry) {
        int snmpVersion = convertSnmpVersionString(version);
        Map<String, Variable> collectedMetrics = new HashMap<>();

        if (snmpVersion == SnmpConstants.version2c) {
            try {
                CommunityTarget target = new CommunityTarget();
                target.setCommunity(new OctetString(community));
                target.setVersion(snmpVersion);
                target.setRetries(retries);
                target.setTimeout(timeout);

                IpAddress address = new IpAddress();
                address.setValue(host.getName());

                if (!address.isValid()) {
                    InetAddress resolved = InetAddress.getByName(host.getName());
                    address.setInetAddress(resolved);
                }

                target.setAddress(GenericAddress.parse(String.format("udp://%s/161", address.toString())));
                TreeUtils treeUtils = new TreeUtils(snmp, new DefaultPDUFactory());

                String oid = metricEntry.getOid();

                List<TreeEvent> events = treeUtils.getSubtree(target, new OID(oid));

                for (TreeEvent event : events) {
                    if (event.getStatus() != TreeEvent.STATUS_OK) {
                        LOG.warn("SNMP bulk query for host: {} oid: {} returned non-ok status {}: {}",
                                host.getName(), oid, event.getStatus(), event.getErrorMessage());
                        continue;
                    }

                    VariableBinding[] variableBindings = event.getVariableBindings();
                    if (variableBindings == null || variableBindings.length == 0) {
                        LOG.warn("SNMP bulk query did not return any thing for {}:{}", host.getName(), oid);
                        continue;
                    }

                    for (VariableBinding vb : variableBindings) {
                        String vbOid = vb.getOid().toDottedString();
                        Variable var = vb.getVariable();

                        String index = vbOid.substring(oid.length() + 1);
                        collectedMetrics.put(index, var);
                    }
                }

                return collectedMetrics;

            } catch (UnknownHostException e) {
                LOG.error("Failed to resolve host {}.", host.getName());
            }
        }
        return collectedMetrics;
    }

    /**
     * Query a scalar metric.
     *
     * @param host SNMP host
     * @param oid the metric oid
     * @return the metric value, null if something wrong
     */
    private Variable queryScalarMetric(SnmpHost host, String oid) {
        int snmpVersion = convertSnmpVersionString(version);
        if (snmpVersion == SnmpConstants.version2c) {

            try {
                CommunityTarget target = new CommunityTarget();
                target.setCommunity(new OctetString(community));
                target.setVersion(snmpVersion);
                target.setRetries(retries);
                target.setTimeout(timeout);

                IpAddress address = new IpAddress();
                address.setValue(host.getName());
                if (!address.isValid()) {
                    InetAddress resolved = InetAddress.getByName(host.getName());
                    address.setInetAddress(resolved);
                }
                target.setAddress(GenericAddress.parse(String.format("udp://%s/161", address.toString())));

                PDU pdu = new PDU();
                pdu.add(new VariableBinding(new OID(oid)));
                pdu.setType(PDU.GET);

                ResponseEvent response = snmp.send(pdu, target);
                PDU responsePdu = response.getResponse();
                if (responsePdu == null) {
                    LOG.warn("SNMP query against device {} for oid {} timed out or not an confirmed PDU",
                            host.getName(), oid);
                }

                if (responsePdu.getErrorStatus() != PDU.noError) {
                    LOG.warn("SNMP query against device {} for oid {} failed for reason {}",
                            host.getName(), oid, responsePdu.getErrorStatusText());
                }

                Variable variable = responsePdu.getVariable(new OID(oid));
                if (variable == null) {
                    LOG.warn("SNMP query against device {} for oid {} returned no value", host.getName(), oid);
                }

                return variable;
            } catch (UnknownHostException e) {
                LOG.error("Failed to resolve host {}.", host.getName());

            } catch (IOException e) {
                LOG.error(String.format("Failed to send SNMP query to host %s for %s", host.getName(), oid), e);
            }
        }

        return null;
    }

    /**
     * Convert the raw SNMP metrics to Kilda format.
     *
     * @param host the Host
     * @param metricEntry the specific metric, for example, txPower
     * @param metrics the list of collected metric values
     */
    private void publishMetrics(SnmpHost host, SnmpMetricEntry metricEntry, Map<String, Variable> metrics) {

        List<SnmpMetricPoint> snmpMetrics = new LinkedList<>();

        for (Map.Entry<String, Variable> entry : metrics.entrySet()) {
            if (!supportedDataTypes.contains(entry.getValue().getSyntax())) {
                LOG.info("Encountered unsupported data type: host={}, metrics={}, value={}",
                        host.getName(),
                        metricEntry.getMetric(),
                        entry.getValue().toString());
                continue;
            }

            String index = entry.getKey();
            // some of the metric has subIndex, like txPower
            if (!index.matches("\\d+(\\.\\d+)?")) {
                LOG.warn("Host {} SNMP {} query returned index {} is not in the form of #[.#].",
                        host.getName(), metricEntry.getMetric(), entry.getKey());
                continue;
            }
            String[] indices = index.split("\\.");
            Integer port = Integer.valueOf(indices[0]);
            Integer subIndex = indices.length == 2 ? Integer.valueOf(indices[1]) : null;


            SnmpMetricPoint snmpMetricPoint = new SnmpMetricPoint(port, subIndex, extractMetricValue(entry.getValue()));
            snmpMetrics.add(snmpMetricPoint);
        }

        // using SnmpMetricData can significantly reduce the number of message send to Kafka queue
        // we have switches of 28 ports, 100 ports
        Map<String, String> tags = new HashMap<>();
        // all tags from host
        tags.putAll(host.getTags());

        SnmpMetricData metricData = new SnmpMetricData(
                host.getName(),
                metricEntry.getMetric(),
                snmpMetrics,
                host.getTags()
        );

        try {
            metricQueue.put(metricData);
        } catch (InterruptedException e) {
            LOG.error("SNMP metric collector interrupted when publish metrics", e);
        }
    }

    private Number extractMetricValue(Variable variable) {
        Number value = null;
        int syntax = variable.getSyntax();
        switch (syntax) {
            //case SMIConstants.SYNTAX_INTEGER:
            //case SMIConstants.SYNTAX_UNSIGNED_INTEGER32:
            case SMIConstants.SYNTAX_INTEGER32:
            case SMIConstants.SYNTAX_GAUGE32:
            case SMIConstants.SYNTAX_COUNTER32:
            case SMIConstants.SYNTAX_TIMETICKS:
                value = variable.toInt();
                break;
            case SMIConstants.SYNTAX_COUNTER64:
                value = variable.toLong();
                break;
            default:
                LOG.warn("SNMP variable syntax type {} is not currently supported", syntax);
        }

        return value;
    }

    private int convertSnmpVersionString(String version) {
        switch (version) {
            case "2":
            case "2c":
            case "v2c":
                return SnmpConstants.version2c;
            case "3":
            case "v3":
                return SnmpConstants.version3;
            default:
                LOG.error("Unknown or unsupported SNMP version {}", version);
                throw new IllegalArgumentException("Unknown SNMP version: " + version);
        }
    }

}
