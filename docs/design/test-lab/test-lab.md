# Test lab

## Goals
Run functional and acceptance tests on hardware and virtual
environment without code change.

## The idea
Create abstract API layer for hardware and virtual environment.

## Diagrams

### Use Case
![UC](./use-case.png)

### Component
![CHW](./component-hw.png)

![CV](./component-v.png)

### Sequence
![CV](./sequence-crud-v.png)

### Lab API

#### Create virtual lab
```
POST <lab-api-host>/api/
BODY: topology format from atdd-staging
RESPONSE: new lab id (int)
```
#### Delete lab
```
DELETE <lab-api-host>/api/<lab_id>
RESPONSE: 200 OK
```
#### Access to lock-keeper
Proxy call to lock-keeper host
```
PROXY <lab-api-host>/api/<lab_id>/lock-keeper
```
#### Access to traffgen
Proxy call to traffgen host
```
PROXY <lab-api-host>/api/<lab_id>/traffgen
```
#### Define hw lab
```
POST <lab-api-host>/api/
BODY: new format TODO: add after impl
RESPONSE: new lab id (int)
```
