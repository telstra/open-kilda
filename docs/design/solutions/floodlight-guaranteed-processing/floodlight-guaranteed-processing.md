# Floodlight: monitorable and guaranteed processing of incoming messages

## The problem 
Floodlight OpenKilda Modules interact with other OpenKilda components via Kafka topics. 
Incoming messages are read from kilda.speaker.* topic by a single consuming thread (reactor) and dispatched to handlers 
via a fixed-sized thread pool.

Currently, the reactor fetches all available messages from the topic and schedules them to be processed in the thread pool. 
Although the number of execution threads is fixed, the thread pool queue of scheduled handlers is unbound. 
That means that the Floodlight fetches, acknowledges, and puts into the queue all messages from the topic.  

This causes the following issues:
- Impossible to monitor whether the Floodlight is capable to handle a load. Regardless of the number of messages sent
to the topic, they all are passed into the in-memory queue. This way, a Kafka topic lag is always close to zero.
- Under heavy load there's a risk to lose messages which are fetched and scheduled, but not processed yet. E.g. JVM may 
crash or be killed.

## Solution overview
The reactor thread in Floodlight OpenKilda Modules should not acknowledge and schedule more messages than the thread pool
can immediately process:
- The execution thread pool has no in-memory queue.
- Every message fetched from Kafka topic is to be acknowledged only when an execution thread for it is allocated.

### Sequence Diagrams

#### Current implementation
![Floodlight Collaboration diagram (Old)](./floodlight-collaboration-old.png "Floodlight Collaboration diagram (Old)")

#### New implementation
![Floodlight Collaboration diagram (New)](./floodlight-collaboration-new.png "Floodlight Collaboration diagram (New)")
