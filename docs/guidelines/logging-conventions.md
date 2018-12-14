# Logging conventions
OpenKilda code is expected to use [SLF4J](https://www.slf4j.org/) as API facade for logging.

### Logging Strategy
The goal is to build a diagnostics toolkit that will give enough information for diagnosing and solving 
the customer problem or system failure.

The following is to be logged:
* Business operations with the context
* Transitions (workflow transition, switch state or flow status)
* Integration points (calls, availability)
* Resources availability (limit reached)
* Service availability (startup, shutdown)

### Levels
Ensure that log messages are appropriate in content and severity:
* **debug** detailed information on the flow through the system (REST request/response, TCP packets). 
            Require no action from the support team.
* **info** important/useful runtime events (service start/stop, configuration details, event causing spinoff processes, 
            scheduled execution, requests sent to external API). 
            Require no action from the support team.
* **warn** runtime situations undesirable or unexpected, but not necessarily "wrong", anything that can potentially 
            cause application oddities, resumable errors (retrying an operation, use of deprecated APIs, 
            misuse of API, missing secondary data). 
            Require to assess potential consequences and apply mitigation measures.
* **error** runtime error fatal to the operation but not to service/application 
            (cant open a required file, missing data). 
            Require urgent intervention from a sysadmin or developer.
* **fatal** critical/severe error forcing a shutdown of service/application, 
            risk of data loss/corruption (db connection error). 
            Require immediate action from a sysadmin or developer.

### Logging of system entities
* Never log sensitive data, like user credentials.
* Endpoint (switch ID and port) must be logged using underscore as a separator, e.g.: ``` 00:00:00:00:00:00:00:01_2 ```
* Always override toString() to give a good representation of the object.

### Best practices
* Avoid multi-line log messages.
* Supply logging context or message with correlationId or other unique identifiers (IDs), this is tremendously helpful 
    when debugging or performing root cause analysis.

### References
* https://wiki.opendaylight.org/view/BestPractices/Logging_Best_Practices
* https://dzone.com/articles/9-logging-sins-in-your-java-applications

