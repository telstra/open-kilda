# gRPC client

## Goals
- Be able to send requests to gRPC server
- Have multiple instances of the client
- Be able to manage load (amount of simultaneous requests) per switch

## The idea
Requests processing by gRPC server on switches might take some time, so we should implement it in asynchronous way (gRPC client library supports it). Since we need to have multiple instances of the client, we also have to create some kind of router/balancer next to them. We can implement gRPC client as a Storm topology; Storm will take over tasks to route requests. 

The bolt responsible for sending requests should wait for the response asynchronously, and do not block the bolt's executions. In other words, this bolt will have a storage for tuples/requests. The tuple will reflect that a response to gRPC requests is received.

Besides, the mechanism for limiting the amount of simultaneous requests to the switch should be implemented. It may be some queue per switch or a separate bolt that will manage requests sending. That limit should be configurable.

## Switches' credentials
Perhaps, we need to store credentials somewhere (it might be neo4j or another DB?). During building the requests, credentials for the target switch should be included into proto request.

## Diagram
![gRPC client topology](./grpc-client.png)