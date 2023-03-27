# Message cookie

## Concept
The goal of this approach is to be able to route responses into a specific handler (the handler that have produced request)
into a specific service into a specific application/storm-bolt.

The message producer must add to the messages information that unambiguously describes a sender (in hierarchical manner): 
a message cookie object. A message recipient must produce a response and copy the message cookie from the request. So, the only 
task for the message recipient in this approach is to copy the message cookie from the request to the response.

Message cookie hierarchy represents/mirrors the code hierarchy that produces this cookie. For example, from innermost to 
outermost layers: `unique-request-id => unique-handler-id => service-id`. During message producing each code layer must
add corresponding cookie layer.

Message cookie must be placed into a message envelope (`BaseMessage` and inheritors in our terminology). It will simplify
its copying on recipient side: a message cookie will not be passed into the code that handles request itself, but it will be copied on the 
level responsible for message decoding/encoding. At same time, on the request producer side, carrier decorators can add
extra message cookie layers during delivering a message payload into the message ejecting level without any interaction with
the message payload object.

Keeping all dispatching information in request/response objects reliefs from keeping it somewhere on a request side, and, as result,
there is no need to track it for invalidation and obsolescence.

![route trip time sequence diagram](message-round-trip.png)

## Wrapping process

Cookie wrapping or layer adding process can be done by a carrier decorator (objects responsible for communication with transport layer). 
I.e. an application produces the service and provides a carrier object, decorated with the object that will add service
name cookie layer to each produced request.

For example, we have an interface defining some carrier that will be used by services and by handlers inside a service:

```java
public interface SomeCarrier {
    void sendCommandToSpeaker(CommandData command, @NonNull MessageCookie cookie);
}
```

We can define a decorator that adds some cookie layer before passing a request to the `sendSpeakerCommand`.

```java
public class SomeCarrierDecorator implements SomeCarrier {
    private final SomeCarrier target;
    private final String layerValue;

    public SomeCarrierDecorator(SomeCarrier target, String layerValue) {
        this.target = target;
        this.layerValue = layerValue;
    }

    public void sendCommandToSpeaker(CommandData command, @NonNull MessageCookie cookie) {
        target.sendCommandToSpeaker(command, new MessageCookie(layerValue, cookie));
    }
}
```

On an application init, we will create a service in this way:

```java
public class App {
    Map<Sting, ServiceBase> services = new Map<>();

    public void init() {
        SomeService someService = new SomeService(new SomeCarrierDecorator(carrier, "some_service_name"));
        serivces.put("some_service_name", someService);
    }
}
```

So, on response, we can locate the required service in this way: 

```java
public class App {
    Map<Sting, ServiceBase> services = new Map<>();

    public void dispatch(MessageData payload, MessageCookie cookie) {
        ServiceBase service = services.get(cookie.getValue());
        if (service != null) {
            service.dispatch(payload, cookie.getNested());
        } else {
            log.error("dispatch error");
        }
    }
}
```

A service itself can add to the carrier one more decorator that is responsible for adding a handler-unique ID cookie layer. 
Dispatching can be continued inside service. So, each code layer processes its own message cookie layer and fully controls
the meaning of a cookie value (on specific layer).

## Some possible tooling improvements

`MessageCookie` can define equals/hashcode methods that do not take into account the value of nested fields/cookies. In this case,
a cookie object itself can be used as a `Map` key during dispatching.
