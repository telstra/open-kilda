# Mininet Rest Frontend

Provides a simple REST API to allow creation of networks within Mininet.  Supported API's and payloads are shown below.  **NOTE** all POST methodes require setting *Content-Type: 'application/json'* in the request header.

## Cleanup

Removes all switches, hosts and links from Mininet.

**Method**: POST

**Endpoint**: /cleanup

**Results**: OK

## Switches

Provides the ability to create a new switch and get status of one or more switches.

### Create Switch
**Method**: POST

**Endpoint**: /switch

**Payload**:

```
{
  "switches": [
    {
      "name": "sw4",
      "dpid": "0000000000000004"
    },
    {
      "name": "sw5",
      "dpid": "0000000000000005"
    }
  ]
}
```

### Get All Switches
**Method**: GET

**Endpoint**: /switch

**Results**:

```
[
  {
    "interface": [
      {
        "status": "OK",
        "mac": null,
        "name": "lo"
      }
    ],
    "connected": false,
    "name": "sw5",
    "dpid": "0000000000000005"
  },
  {
    "interface": [
      {
        "status": "OK",
        "mac": null,
        "name": "lo"
      }
    ],
    "connected": false,
    "name": "sw4",
    "dpid": "0000000000000004"
  }
]
```

### Get One Switch
**Method**: GET

**Endpoint**: /switch/<switch_name>

**Results**:

```
{
  "interface": [
    {
      "status": "OK",
      "mac": null,
      "name": "lo"
    }
  ],
  "connected": true,
  "name": "sw4",
  "dpid": "0000000000000004"
}
```

## Links

Provides the ability to create a new link and get status of all links.

### Create Link
**Method**: POST

**Endpoint**: /links

**Payload**:

```
{
  "links": [
    {
      "node1": "sw5",
      "node2": "sw4"
    }
  ]
}
```

### Get Links
**Method**: GET

**Endpoint**: /links

**Results**:

```
[
  {
    "status": "(OK OK)",
    "name": "sw5-eth1:sw4-eth1"
  }
]
```

## Controller

Provides the ability to create a new controller and get status of all controllers.

### Create Controller
**Method**: POST

**Endpoint**: /controller

**Payload**:

```
{
  "controllers": [
  	{
  		"name": "floodlight",
  		"host": "192.168.56.1",
  		"port": 6653
  	}	
  ]
}
```

### Get Controllers
**Method**: GET

**Endpoint**: /controller

**Results**:

```
[
  {
    "host": "192.168.56.1",
    "name": "floodlight",
    "port": 6653
  }
]
```

## Topology

The topology endpoint allows sending in a complete topology in a single API call.

### Create Topology
**Method**: POST

**Endpoint**: /topology

**Payload**:

```
{
  "controllers": [
  	{
  		"name": "floodlight",
  		"host": "192.168.56.1",
  		"port": 6653
  	}	
  ],
  "links": [
    {
      "node1": "sw1",
      "node2": "sw2"
    }
  ],
  "switches": [
    {
      "name": "sw1",
      "dpid": "0000000000000001"
    },
    {
      "name": "sw2",
      "dpid": "0000000000000002"
    }
  ]
}
```

### Get Topology
**Method**: GET

**Endpoint**: /topology

Returns back the created topology.

```
{
  "controllers": [
    {
      "host": "192.168.56.1",
      "name": "floodlight",
      "port": 6653
    }
  ],
  "switches": [
    {
      "interface": [
        {
          "status": "OK",
          "mac": null,
          "name": "lo"
        },
        {
          "status": "OK",
          "mac": null,
          "name": "sw1-eth1"
        }
      ],
      "connected": true,
      "name": "sw1",
      "dpid": "0000000000000001"
    },
    {
      "interface": [
        {
          "status": "OK",
          "mac": null,
          "name": "lo"
        },
        {
          "status": "OK",
          "mac": null,
          "name": "sw2-eth1"
        }
      ],
      "connected": true,
      "name": "sw2",
      "dpid": "0000000000000002"
    },
  ],
  "links": [
    {
      "status": "(OK OK)",
      "name": "sw2-eth1:sw1-eth1"
    }
  ]
}
```

## TODO

1. create_host
2. shutdown link
3. shutdown switch
4. block traffic via iptables on a link (simulate failure at the core of a link while the ports stay up.
5. dump_flows from the switches
6. shutdown a controller
7. specify controller(s) a switch is connected to
8. ping between hosts
