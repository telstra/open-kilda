# **OPEN KILDA GUI**

## Introduction

### Overview

This project holds the OPEN KILDA GUI service for Open Kilda Controller.

### Documentation

#### Configuration

* The **_openkilda-gui_** feature must be installed in OPEN KILDA.
* The GUI listens on port 8010 if we run it using docker-compose and/or make commands
	+ These values can be changed in ```src/main/resources/application.properties``` file:
		``` server.port = 1010```
		
* The base application context is ```/openkilda```
	+ for example, to access the GUI on _localhost_, use
    `http://localhost:8010/openkilda` 
	
* In Base url as VM_IP address where controller will be deployed.
	+ Default as localhost (127.0.0.1) :
  
		`base.url = 127.0.0.1`

These values can be changed in ```src/main/resources/application.properties``` file:

``` base.url = http://127.0.0.1```


* Northbound API services default username and password are:
  + kilda
  + kilda
  
  It uses Basic Authentication Scheme in some api call.

These values could be changed in ```src/main/resources/application.properties``` file:
```bash
kilda.username = kilda
kilda.password = kilda
```

----------
### **How to Build Open Kilda GUI**

From the openkilda-gui directory run these commands:

+ ```make build-nc-openkilda```


### **How to Run Open Kilda GUI**

__NOTE: To run Open Kilda GUI, you should have built it already (ie the previous section).__
This is particularly important because Makefile will expect some of the
containers to already exist.

From the openkilda-gui directory run these commands:

+ ```make run```

Some other commands will run in  openkilda-gui directory :

+ ```make build-openkilda```
+ ```make stop```

----

### User Documentation

> See `README.user.releases.md` for view user documentation.

[README.user.releases]: README.user.releases.md