# Problem

OpenKilda as an SDN controller should be able to provide not only core functions but also support applications.
This design is initial proposal for integration of one the Telescope application with controller.

# Model

It should be possible to add/remove application for the single flow endpoint. From DB perspective  new fields will be 
added to `Flow` node
2 Applications on source endpoint:  

    ```
    {
      "src_apps": ["APP1", "APP2" ],
      "dst_apps": []
    }
    ```

Application on source and destination endpoint:

    ```
    {
      "src_apps": ["APP1"],
      "dst_apps": ["APP2"]
    }
    ```

From operations perspective this change requires DB migration.

# API

## NB API for Flows

Northbound will provide following endpoints for the flow applications

`GET /flows/:flowid/applications` - returns list of all enabled applications for the flow

`PATCH /flows/:flowid/applications/:appname` - update list of enabled apps for the flow

`DELETE /flows/:flowid/applications/appname` - remove app for the flow


## Kafka API

### Messaging
Command for the new exclusion on flow endpoint
    
    ```{"clazz":"org.openkilda.applications.command.CommandMessage",
        "destination":"CONTROLLER",
        "payload":{"clazz":"org.openkilda.applications.command.apps.CreateExclusion",
                   "flow-id": "string",
           		"endpoint": {
               		"switch-id": "string",
               		"port": 0,
               		"vlan": 0
           		},
           		"exclusion":
                	{
                   	 "src_ip": "string",
                   	 "src_port": 0,
                    	 "dst_ip": "string"
                    	 "dst_port": 0,
                        "proto": "string"
                   }
           },
        "timestamp":0,
        "correlation_id":"string"
       }```
       
Notification of the new exclusion addition result:

       ```{"clazz":"org.openkilda.applications.messaging.info.InfoMessage",
        "payload":{"clazz":"org.openkilda.applications.command.apps.CreateExclusionResult",
                    "flow-id": "string",
                    "endpoint": {
                        "switch-id": "string",
                        "port": 0,
                        "vlan": 0
                    },
                    "application": "telescope",
                    "success": true
                   },
        "timestamp":0,
        "correlation_id":"string"
       }```       
    
Command for exclusion removal:    
 
     ```{"clazz":"org.openkilda.applications.messaging.command.CommandMessage",
         "destination":"CONTROLLER",
         "payload":{"clazz":"org.openkilda.applications.messaging.command.apps.RemoveExclusion",
                    "flow-id": "string",
            		"endpoint": {
                		"switch-id": "string",
                		"port": 0,
                		"vlan": 0
            		},
            		"exclusion":
                 	{
                    	 "src_ip": "string",
                    	 "src_port": 0,
                     	 "dst_ip": "string"
                     	 "dst_port": 0,
                         "proto": "string"
                    }
            },
         "timestamp":0,
         "correlation_id":"string"
        }```
        
Notification of the exclusion removal result:

       ```{"clazz":"org.openkilda.applications.messaging.info.InfoMessage",
        "payload":{"clazz":"org.openkilda.applications.command.apps.RemoveExclusionResult",
                    "flow-id": "string",
                    "endpoint": {
                        "switch-id": "string",
                        "port": 0,
                        "vlan": 0
                    },
                    "application": "telescope",
                    "success": true
                   },
        "timestamp":0,
        "correlation_id":"string"
       }```               

Notification of the new flow endpoint to watch:

    ```{"clazz":"org.openkilda.applications.messaging.info.InfoMessage",
        "payload":{"clazz":"org.openkilda.applications.messaging.info.apps.FlowApplicationCreated",
                    "flow-id": "string",
                    "endpoint": {
                        "switch-id": "string",
                        "port": 0,
                        "vlan": 0
                    },
                    "application": "telescope"
                   },
        "timestamp":0,
        "correlation_id":"string"
       }```

Notification of the flow endpoint to stop watch:

    ```{"clazz":"org.openkilda.applications.messaging.info.InfoMessage",
        "payload":{"clazz":"org.openkilda.applications.messaging.info.apps.FlowApplicationRemoved",
                    "flow-id": "string",
                    "endpoint": {
                        "switch-id": "string",
                        "port": 0,
                        "vlan": 0
                    },
                    "application": "telescope"
                   },
        "timestamp":0,
        "correlation_id":"string"
       }```


### Topics

To communicate with application OpenKilda uses public kafka topics:
 - `kilda.apps.pub` - this topic should be used for adding/removing applications for the flow endpoint and
 actions originated by application itself.
 - `kilda.apps.notifications.pub` - this topic should be used for notifications of creation/removal apps for flow
  endpoint, result of execution app specific commands.
 - `kilda.stats.pub` - public feed for statistics
 
 

# WorkFlow
![create-app-for-flow-sequence](./create_app_for_flow_sequence.png "Enable application for the flow")

![remove-app-for-flow-sequence](./remove_app_for_flow_sequence.png "Disable application for the flow")

![update-exclusions](./update_exclusions.png "Update app exclusions")




