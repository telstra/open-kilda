package org.openkilda.atdd.staging.service.traffexam;

import org.openkilda.atdd.staging.model.topology.TopologyDefinition;
import org.openkilda.atdd.staging.model.topology.TopologyDefinition.Trafgen;
import org.openkilda.atdd.staging.model.topology.TopologyDefinition.TrafgenConfig;
import org.openkilda.atdd.staging.service.traffexam.model.Address;
import org.openkilda.atdd.staging.service.traffexam.model.AddressResponse;
import org.openkilda.atdd.staging.service.traffexam.model.ConsumerEndpoint;
import org.openkilda.atdd.staging.service.traffexam.model.Endpoint;
import org.openkilda.atdd.staging.service.traffexam.model.EndpointAddress;
import org.openkilda.atdd.staging.service.traffexam.model.EndpointReport;
import org.openkilda.atdd.staging.service.traffexam.model.EndpointResponse;
import org.openkilda.atdd.staging.service.traffexam.model.Exam;
import org.openkilda.atdd.staging.service.traffexam.model.ExamReport;
import org.openkilda.atdd.staging.service.traffexam.model.ExamResources;
import org.openkilda.atdd.staging.service.traffexam.model.Host;
import org.openkilda.atdd.staging.service.traffexam.model.HostResource;
import org.openkilda.atdd.staging.service.traffexam.model.ProducerEndpoint;
import org.openkilda.atdd.staging.service.traffexam.model.ReportResponse;
import org.openkilda.atdd.staging.service.traffexam.networkpool.Inet4Network;
import org.openkilda.atdd.staging.service.traffexam.networkpool.Inet4NetworkPool;
import org.openkilda.atdd.staging.service.traffexam.networkpool.Inet4ValueException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.Inet4Address;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class TraffExamServiceImpl implements TraffExamService, DisposableBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(TraffExamServiceImpl.class);

    @Autowired
    @Qualifier("traffExamRestTemplate")
    private RestTemplate restTemplate;

    private Map<UUID, Host> hostsPool = new HashMap<>();

    private Inet4NetworkPool addressPool;

    private Map<UUID, Address> suppliedAddresses = new HashMap<>();
    private Map<UUID, HostResource> suppliedEndpoints = new HashMap<>();
    private List<HostResource> failedToRelease = new LinkedList<>();

    public TraffExamServiceImpl(TopologyDefinition topology) {
        for (Trafgen trafgen : topology.getActiveTrafgens()) {
            UUID id = UUID.randomUUID();
            try {
                Host host = new Host(
                        id, trafgen.getIfaceName(), new URI(trafgen.getControlEndpoint()), trafgen.getName());
                hostsPool.put(id, host);
            } catch (URISyntaxException e) {
                throw new InputMismatchException(String.format(
                        "Invalid trafgen(%s) REST endpoint address \"%s\": %s",
                        trafgen.getName(), trafgen.getControlEndpoint(), e));
            }
        }

        TrafgenConfig config = topology.getTrafgenConfig();
        Inet4Network network;
        try {
            network = new Inet4Network(
                    (Inet4Address) Inet4Address.getByName(config.getAddressPoolBase()),
                    config.getAddressPoolPrefixLen());
        } catch (Inet4ValueException | UnknownHostException e) {
            throw new InputMismatchException(String.format(
                    "Invalid trafgen address pool \"%s:%s\": %s",
                    config.getAddressPoolBase(), config.getAddressPoolPrefixLen(), e));
        }
        addressPool = new Inet4NetworkPool(network, 30);
    }

    @Override
    public List<Host> listHosts() {
        return new ArrayList<>(hostsPool.values());
    }

    @Override
    public Host hostByName(String name) throws NoResultsFoundException {
        if (name == null) {
            throw new IllegalArgumentException("Argument \"name\" must not be null");
        }

        Host target = null;
        for (Host current : hostsPool.values()) {
            if (!name.equals(current.getName())) {
                continue;
            }
            target = current;
            break;
        }

        if (target == null) {
            throw new NoResultsFoundException(
                    String.format("There is no host with name \"%s\"", name));
        }

        return target;
    }

    @Override
    public Exam startExam(Exam exam)
            throws NoResultsFoundException, OperationalException {
        checkHostPresence(exam.getSource());
        checkHostPresence(exam.getDest());

        Inet4Network subnet;
        try {
            subnet = addressPool.allocate();
        } catch (Inet4ValueException e) {
            throw new OperationalException(
                    "Unable to allocate subnet for exam. There is no more " +
                    "addresses available.");
        }

        ExamResources resources = null;
        List<HostResource> supplied = new ArrayList<>(4);
        try {
            Address sourceAddress = new Address(
                    subnet.address(1), subnet.getPrefix());
            sourceAddress.setVlan(exam.getSourceVlan());
            sourceAddress = assignAddress(exam.getSource(), sourceAddress);
            supplied.add(sourceAddress);

            Address destAddress = new Address(
                    subnet.address(2), subnet.getPrefix());
            destAddress.setVlan(exam.getDestVlan());
            destAddress = assignAddress(exam.getDest(), destAddress);
            supplied.add(destAddress);

            ConsumerEndpoint consumer = (ConsumerEndpoint) assignEndpoint(
                    exam.getDest(), new ConsumerEndpoint(destAddress.getId()));
            supplied.add(consumer);

            ProducerEndpoint producer = new ProducerEndpoint(
                    sourceAddress.getId(),
                    new EndpointAddress(destAddress.getAddress(), consumer.getBindPort()));
            if (exam.getBandwidthLimit() != null) {
                producer.setBandwidth(exam.getBandwidthLimit());
            }
            if (exam.getTimeLimitSeconds() != null) {
                producer.setTime(exam.getTimeLimitSeconds());
            }

            producer = (ProducerEndpoint) assignEndpoint(exam.getSource(), producer);
            supplied.add(producer);

            resources = new ExamResources(subnet, producer, consumer);
        } catch (Inet4ValueException e) {
            throw new OperationalException(
                    "Insufficient resources - not enough IP address in subnet. Check addressPool configuration.");
        } finally {
            if (resources == null) {
                extendFailedToRelease(releaseResources(supplied));

                try {
                    addressPool.free(subnet);
                } catch (Inet4ValueException e) {
                    // Unreachable point, free throw exception only if invalid (not allocated before) address passed
                }
            }
        }

        exam.setResources(resources);
        return exam;
    }

    public List<ExamReport> waitExam(List<Exam> exams) {
        return this.waitExam(exams, true);
    }

    @Override
    public List<ExamReport> waitExam(List<Exam> exams, boolean cleanup) {
        List<ExamReport> results = new ArrayList<>(exams.size());

        for (Exam current : exams) {
            // current backend implementation do not allow infinite
            // execution, so we can have infinite loop here
            while (true) try {
                try {
                    results.add(fetchReport(current));
                    break;
                } catch (ExamNotFinishedException e) {
                    TimeUnit.SECONDS.sleep(1);
                }
            } catch (InterruptedException e) {
                // ignore interrupt exceptions
            }

            if (cleanup) {
                stopExam(current);
            }
        }

        return results;
    }

    @Override
    public ExamReport fetchReport(Exam exam) throws NoResultsFoundException, ExamNotFinishedException {
        ExamResources resources = retrieveExamResources(exam);

        EndpointReport producerReport = fetchEndpointReport(resources.getProducer());
        EndpointReport consumerReport;
        try {
            consumerReport = fetchEndpointReport(resources.getConsumer());
        } catch (ExamNotFinishedException e) {
            if (producerReport.getError() == null) {
                throw e;
            }
            consumerReport = new EndpointReport("Don't wait for consumer report due to error on producer side");
        }

        return new ExamReport(exam, producerReport, consumerReport);
    }

    @Override
    public void stopExam(Exam exam) throws NoResultsFoundException {
        ExamResources resources = retrieveExamResources(exam);
        List<HostResource> releaseQueue = new ArrayList<>(4);

        releaseQueue.add(resources.getProducer());
        releaseQueue.add(resources.getConsumer());

        UUID addressId;

        Address address;
        addressId = resources.getProducer().getBindAddressId();
        if (addressId != null) {
            address = suppliedAddresses.get(addressId);
            checkHostRelation(address, suppliedAddresses);
            releaseQueue.add(address);
        }
        addressId = resources.getConsumer().getBindAddressId();
        if (addressId != null) {
            address = suppliedAddresses.get(addressId);
            checkHostRelation(address, suppliedAddresses);
            releaseQueue.add(address);
        }

        List<HostResource> failed = releaseResources(releaseQueue);
        try {
            // release time is not time critical so we can try to retry release call for "stuck" resources here
            retryResourceRelease();
        } finally {
            extendFailedToRelease(failed);
        }
    }

    @Override
    public void stopAll() {
        List<HostResource> releaseQueue = new LinkedList<>();

        releaseQueue.addAll(suppliedEndpoints.values());
        releaseQueue.addAll(suppliedAddresses.values());

        releaseQueue = releaseResources(releaseQueue);
        try {
            retryResourceRelease();
        } finally {
            extendFailedToRelease(releaseQueue);
        }
    }

    @Override
    public void destroy() throws Exception {
        stopAll();
    }

    private Address assignAddress(Host host, Address payload) {
        AddressResponse response = restTemplate.postForObject(
                makeHostUri(host).path("address").build(), payload,
                AddressResponse.class);

        Address address = response.address;
        address.setHost(host);
        suppliedAddresses.put(address.getId(), address);

        return address;
    }

    private void releaseAddress(Address subject) {
        restTemplate.delete(
                makeHostUri(subject.getHost())
                        .path("address/")
                        .path(subject.getId().toString()).build());

        suppliedAddresses.remove(subject.getId());
        subject.setHost(null);
    }

    private Endpoint assignEndpoint(Host host, Endpoint payload) {
        EndpointResponse response = restTemplate.postForObject(
                makeHostUri(host).path("endpoint").build(),
                payload, EndpointResponse.class);

        Endpoint endpoint = response.endpoint;
        endpoint.setHost(host);
        suppliedEndpoints.put(endpoint.getId(), endpoint);

        return endpoint;
    }

    private void releaseEndpoint(Endpoint endpoint) {
        restTemplate.delete(
                makeHostUri(endpoint.getHost())
                        .path("endpoint/")
                        .path(endpoint.getId().toString())
                        .build());

        suppliedEndpoints.remove(endpoint.getId());
    }

    private EndpointReport fetchEndpointReport(Endpoint endpoint) throws NoResultsFoundException, ExamNotFinishedException {
        checkHostRelation(endpoint, suppliedEndpoints);

        ReportResponse report = restTemplate.getForObject(
                makeHostUri(endpoint.getHost())
                        .path("endpoint/")
                        .path(endpoint.getId().toString())
                        .path("/report").build(),
                ReportResponse.class);
        if (report.getStatus() == null) {
            throw new ExamNotFinishedException();
        }

        return new EndpointReport(report);
    }

    private synchronized void retryResourceRelease() {
        failedToRelease = releaseResources(failedToRelease);
    }

    private List<HostResource> releaseResources(List<HostResource> resources) {
        List<HostResource> fail = new LinkedList<>();

        for (HostResource item : resources) {
            try {
                if (item instanceof Address) {
                    releaseAddress((Address) item);
                } else if (item instanceof Endpoint) {
                    releaseEndpoint((Endpoint) item);
                } else {
                    throw new RuntimeException("Unsupported resource");
                }
            } catch (HttpStatusCodeException e) {
                if (e.getStatusCode() != HttpStatus.NOT_FOUND) {
                    fail.add(item);
                }
            } catch (RestClientException e) {
                fail.add(item);
            }
        }

        return fail;
    }

    private synchronized void extendFailedToRelease(List<HostResource> resources) {
        failedToRelease.addAll(resources);
    }

    private ExamResources retrieveExamResources(Exam exam) throws NoResultsFoundException {
        ExamResources resources = exam.getResources();
        if (resources == null) {
            throw new IllegalArgumentException("Exam resources are empty.");
        }
        checkExamRelation(resources);

        return resources;
    }

    private void checkExamRelation(ExamResources resources) throws NoResultsFoundException {
        checkHostRelation(resources.getProducer(), suppliedEndpoints);
        checkHostRelation(resources.getConsumer(), suppliedEndpoints);
    }

    private void checkHostRelation(
            HostResource target, Map<UUID, ? extends HostResource> supplied)
            throws NoResultsFoundException {
        if (!supplied.containsKey(target.getId())) {
            throw new NoResultsFoundException(
                    "Object is not supplied by this service.");
        }
        if (target.getHost() == null) {
            throw new NoResultsFoundException(
                    "Object have no link to the host object.");
        }
    }

    private void checkHostPresence(Host subject)
            throws NoResultsFoundException {
        if (!hostsPool.containsKey(subject.getId())) {
            throw new NoResultsFoundException(String.format(
                    "There is no host with id \"%s\"", subject.getId()));
        }
    }

    private UriBuilder makeHostUri(Host host) {
        return UriComponentsBuilder.fromUri(host.getApiAddress());
    }
}
