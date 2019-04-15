# Changelog

## v1.17.1

### Bug Fixes:
-  [#2259](https://github.com/telstra/open-kilda/pull/2259) Propagate ISL's cost updates to the link-props objects. (Issues:  [#2257](https://github.com/telstra/open-kilda/issues/2257))

---
To see the full list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.17.0...v1.17.1).

## v1.17.0

### Enhancements:
-  [#2161](https://github.com/telstra/open-kilda/pull/2161) Combine `api` and `northbound` modules, prepare Northbound for API v2 support

### Bug Fixes:
-  [#2236](https://github.com/telstra/open-kilda/pull/2236) Fix the network dump issue in Floodlight router (Router doesn't set proper switch statuses when a region goes back up after being offline).
-  [#2242](https://github.com/telstra/open-kilda/pull/2242) Fix anonymous authentication for Northbound health-check ([#2161](https://github.com/telstra/open-kilda/pull/2161) broke anonymous authentication for `/api/v1/health-check`)

### Improvements:
-  [#2231](https://github.com/telstra/open-kilda/pull/2231) Add logging of stacktrace in DiscoveryBolt exception handling
-  [#2227](https://github.com/telstra/open-kilda/pull/2227) Add storm.yaml template config in confd
-  [#2235](https://github.com/telstra/open-kilda/pull/2235) Enable OVS virtual meters by default [**area/testing**]
-  [#2179](https://github.com/telstra/open-kilda/pull/2179) Add test: System is able to reroute flow in the correct order based on the priority field [**area/testing**] (Issues:  [#2144](https://github.com/telstra/open-kilda/issues/2144))

---
To see the full list of changes, check out [the commit log](https://github.com/telstra/open-kilda/compare/v1.16.2...v1.17.0).