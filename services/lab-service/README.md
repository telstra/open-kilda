## Lab Service
This is a service for unifying access to hardware and virtual network environments.
Divides on `api` and `service` parts. [Read more](https://github.com/telstra/open-kilda/blob/develop/docs/design/test-lab/test-lab.md)

### Useful commands
Flush all defined topologies:
```
curl -X POST localhost:8288/api/flush
```
Get all defined topologies:
```
curl localhost:8288/api
```

## Traffic Examination
A REST API wrapper above iperf. [Read more](https://github.com/telstra/open-kilda/blob/develop/services/lab-service/traffexam/README.rst)