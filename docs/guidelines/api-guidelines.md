# OpenKilda REST API Design Guidelines

The document provides general guidance on principles and conventions used during development of OpenKilda REST API. 
It is addressed to developers who design or enhance Northbound API, Floodlight Modules REST or another REST API in OpenKilda.

# Principles

### OpenKilda REST API Design Principles

* Simplicity, comprehensibility, and usability of APIs.
* Prefer REST-based API with JSON payload.
* Follow the RESTful architectural style as much as possible. 

    Although per definition RESTful API has to support HATEOAS (maturity level 3). 
    Our guidelines don't enforce full RESTful compliance, but limited hypermedia support. 
    However, we use "RESTful API" to refer implementation of 
    the [REST Maturity level 2](https://martinfowler.com/articles/richardsonMaturityModel.html#level2).

### What is RESTful API?

The RESTful architectural style describes six constraints:
* Client-Server    
* Stateless
* Cacheable
* Layered System
* Code on Demand (optional)
* Uniform Interface

A web service which violates any of the required constraints **cannot be considered RESTful**.

For a more detailed overview, check out:
* [REST on Wikipedia](https://en.wikipedia.org/wiki/Representational_state_transfer)
* [Architectural Styles and the Design of Network-based Software Architectures](https://www.ics.uci.edu/~fielding/pubs/dissertation/rest_arch_style.htm) - 
the chapter on REST in Roy Fielding's dissertation on Network Architecture.
* [RFC 7231](https://tools.ietf.org/html/rfc7231)

# Conventions & Rules

### Resource

URIs follow [RFC 3986](https://tools.ietf.org/html/rfc3986) specification.

#### URI Naming Conventions

[Resource Naming](https://www.restapitutorial.com/lessons/restfulresourcenaming.html)

* Use nouns and NOT the verbs.
* Use the plural version of a resource name, e.g.:
```
/switches
/flows
/links
```
* Use lowercase and dash-separated path names, e.g.:
```
/health-check
```
* Use snake_case (underscore-separated) query string literals.

### Use HTTP methods to operate on collections and entities

[HTTP methods](https://www.restapitutorial.com/lessons/httpmethods.html)

E.g.

| Resource / HTTP method | POST (create)    | GET (read)  | PUT (update)           | PATCH (partial update) | DELETE (delete)    |
| ---------------------- | ---------------- | ----------- | ---------------------- | ---------------------- | ------------------ |
| /flows                 | Create new flow  | List flows  | Error                  | Error                  | Error              |
| /flows/{flow_id}       | Error            | Get flow    | Update flow if exists  | Partially update flow  | Delete flow        |

#### Create entity

* Server returns the `HTTP 201 Created` status code
* Response will also include the `Location` header that points to the URL of the new resource ([RFC 6570](http://tools.ietf.org/html/rfc6570))
* Server returns the new entity

#### List entities (collection)

* Response collection is presented as an array wrapped in a root object. 
The corresponding field has the same name as the resource. See [Wrap collection in object](#wrap-collection-in-object)

#### Update entity

* To update an entity, the server requires being provided with all the properties that can be subject to update
* Server returns an updated entity

#### Partially update entity

* Partially update an entity using [JSON Merge](https://tools.ietf.org/html/rfc7396) format. 
The server requires being provided with the properties that can be subject to update.
* Server returns an updated entity

#### Delete entity

* Server returns the `HTTP 204 No Content` status code if response content is empty.

### Return appropriate status codes

[HTTP Status Codes](http://www.restapitutorial.com/httpstatuscodes.html)

Return appropriate HTTP status codes with each response.

Responses concerning successful operations should be coded according to the following rules:

* `200 Ok`: Successful execution of `GET`, `PUT`, `PATCH` request. An existing resource is updated synchronously.
* `201 Created`: Response to a `POST` request that results in entity creation.
* `202 Accepted`: Accepted request concerning a `POST`, `PUT`, `DELETE`, or `PATCH` request that will be processed asynchronously.
* `204 No Content`: Response to a successful request that will return empty body (as a `DELETE` request)

Pay attention to the use of authentication and authorization error codes:

* `401 Unauthorized`: Request failed because a user is not authenticated
* `403 Forbidden`: Request failed because a user does not have authorization to access a specific resource

Return suitable codes to provide additional information in case of errors:

* `400 Bad Request`: The request is malformed (the request body does not parse). Required field is not provided. 
* `404 Not Found`: Requesting for a resource that does not exist
* `422 Unprocessable Entity`: Used for cases when request is correct, but a server was unable to process the contained instructions

* `500 Internal Server Error`: Something is wrong with the server
* `501 Not Implemented`: This feature is not ready yet

### Validate request parameters

#### Validate errors

For error validation, return `400 Bad Request` or `HTTP 422 Unprocessable Entity` response body with an error or list of errors.

### Representation

* Representation entities follow the JSON Data Interchange Syntax as described in [RFC 7159](https://tools.ietf.org/html/rfc7159).

#### Property name format

Use snake_case (underscore-separated) for parameters and properties (e.g. `first_name`).

Array types should have plural property names.

#### Null values

Blank fields are generally omitted instead of being blank strings or `null`.

#### Empty collections

If you want to return empty collection, return `[]` instead of `null`.

#### Wrap collection in object

Always return root element as an object. This way you can add extra fields to the response without compatibility breakdown.

#### Use UTC times formatted in ISO8601

Accept and return times in UTC only. Render times as a string ISO8601 format (yyyy-MM-dd'T'HH:mm:ss.SSSZ), e.g.:
```
"created_at": "2012-01-01T12:00:00.000Z",
"updated_at": "2012-01-01T13:00:00.000Z",
```

#### Time without date

Render time value as a string in format (HH:mm:ss.SSS) without time zone information, e.g.:
```
"from": "08:00:00.000",
"to": "16:00:00.000"
```

#### Enum values

Enum values are represented as uppercase strings.

Java code:
```java
public enum Color {
  WHITE,
  BLACK,
  RED,
  YELLOW,
  BLUE
}
```

JSON Object:
```json
{
  "color": "WHITE"
}
```

#### Nesting foreign resources relations

Nest foreign resources references if more than only `id` of the object referred, e.g.:
```json
{
  "flow_id": "01234567",
  "src_switch": {
    "id": "5d8201b0...",
    "name": "sw1"
  },
  // ...
}
```

Instead of a flat structure e.g.:
```json
{
  "flow_id": "01234567",
  "src_switch_id": "5d8201b0...",
  "src_switch_name": "sw1",
  // ...
}
```

#### Provide full resources where available

Provide the full resource representation (i.e. the object with all properties) whenever possible in the response.

### Filtering

* Use a query parameter for each field that supports filtering.
* If you want to filter many values in one field, you should repeat the field name many times in URL adding different values.

### Command pattern

In many cases CRUD operations can be easily mapped to HTTP methods. However, that is not the case for more complex operations that 
causing changes in many integrated services. For them the following pattern can used:

#### A command on a resource

* Use verbs.
```
POST https://{host}/flows/{flow_id}/validate
```

# Documentation

Each resource method has to provide brief documentation for itself:
* A description of what given resource is responsible for.
* Expected input parameters (`GET`) or body (`POST`, `PUT`, `PATCH`), required and optional parameters.
* The response a client should expect.
* Possible error codes.