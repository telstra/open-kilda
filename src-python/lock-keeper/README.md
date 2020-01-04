A Lock keeper looks after a canal or river lock, operating it and if 
necessary maintaining it or organizing its maintenance. Traditionally, 
lock keepers live on-site, often in a small purpose-built cottage.

Basic sw topology

```
                      ______________
[kilda-sw-01]--[ISL]--|intermediate |--[ISL]--[kilda-sw-02]
[kilda-sw-03]--[ISL]--|      sw     |--[ISL]--[kilda-sw-04]
                      ‾‾‾‾‾‾‾‾‾‾‾‾‾‾
```                      

Main purpose on that service is get HTTP rest request and configure OF 
rules on intermediate sw.
Also, it can start/stop controller's container to simulate its outage or maintenance.

Build

docker build -t kilda/lock-keeper .

Run

docker run -p 5001:5001 -v /var/run/docker.sock:/var/run/docker.sock -d --rm -e 
LOCK_KEEPER_HOST=XXXX -e LOCK_KEEPER_USER=XXXX -e LOCK_KEEPER_SECRET=XXXX 
--name=lock-keeper kilda/lock-keeper
