# LAG for ports

## Overview

Link aggregation provides ability to combine multiple physical connections into one logical connection to improve resiliency. Link aggregation group (LAG) is a group of ports associated with the logical port on the switch.

## API

Shell variables required by API examples:
~~~shell
nb_host="localhost:8080"
switch_id="00:00:00:00:00:00:00:01"
auth_login="kilda"
auth_password="kilda"
~~~

### Create new LAG

Request format:

~~~shell
curl -X POST --location "http://${nb_host}/api/v2/switches/${switch_id}/lags" \
    -H "accept: */*" \
    -H "correlation_id: ${request_id:-dummy_correlation_id}" \
    -H "Content-Type: application/json" \
    --basic --user "${auth_login}:${auth_password}" \
    -d @- << POST_DATA
{ "port_numbers": [ 40, 41 ] }
POST_DATA
~~~

Response example:

~~~json
{
  "logical_port_number": 2891,
  "port_numbers": [
    40,
    41
  ]
}
~~~


### Read all LAGs on specific switch

Request format:

~~~shell
curl -X GET --location "http://${nb_host}/api/v2/switches/${switch_id}/lags" \
    -H "accept: */*" \
    -H "correlation_id: ${request_id:-dummy_correlation_id}" \
    --basic --user "${auth_login}:${auth_password}"
~~~

Response example:

~~~json
[
  {
    "logical_port_number": 2891,
    "port_numbers": [
      40,
      41
    ]
  },
  {
    "logical_port_number": 2198,
    "port_numbers": [
      22,
      27
    ]
  }
]
~~~


### Update specific LAG on specific switch

Request format:

~~~shell
port_number=2198

curl -X PUT --location "http://${nb_host}/api/v2/switches/${switch_id}/lags/${port_number}" \
    -H "accept: */*" \
    -H "correlation_id: ${request_id:-dummy_correlation_id}" \
    -H "Content-Type: application/json" \
    --basic --user "${auth_login}:${auth_password}" \
    -d @- << PUT_DATA
{ "port_numbers": [ 35, 36 ] }
PUT_DATA
~~~

Response example:

~~~json
{
  "logical_port_number": 2198,
  "port_numbers": [
    35,
    36
  ]
}
~~~


### Delete specific LAG on specific switch

Request format:

~~~shell
port_number=2198

curl -X DELETE --location "http://${nb_host}/api/v2/switches/${switch_id}/lags/${port_number}" \
    -H "accept: */*" \
    -H "correlation_id: ${request_id:-dummy_correlation_id}" \
    -H "Content-Type: application/json" \
    --basic --user "${auth_login}:${auth_password}"
~~~

Response example:

~~~json
{
  "logical_port_number": 2198,
  "port_numbers": [
    35,
    36
  ]
}
~~~

## Details
All logical port related commands are sent to the switches using gRPC speaker.

Kilda configuration defines logical port numbers range and amount of chunks in this range. During LAG create operation
random chunk number will be selected and first unassigned number from this chunk will be used as logical port number.
Port number allocation done on per switch basis, so different switches can have LAG logical ports with same numbers. 

It is not allowed to have one physical port in two LAGs so this rule will provide unique logical port number for any 
correct port configuration. LAG logical port configuration should be validated before any create operation to avoid 
inconsistency. 

Currently, open-kilda doesn't have any port related information representation in database. We need to save LAG logical port configuration into database to have ability to restore configuration on the switch. Information about LAGs stored as a separate models in order to provide minimal impact on already existing data structures.

![domain-model](./domain-model.png)

Open-kilda uses a switch-port pair to represent a flow endpoint. LAG ports created in this way may be used as a flow endpoint on one or both flow sides to provide flow resiliency.

## Additional changes

During switch/flow validate and sync LAG ports configuration should be checked and installed if required. 
