# OpenKilda TSDB dump/restore tool

This packet contains python package that provide `kilda-otsdb-dump-restore` CLI tool. The tool itself
provides following commands:
* `kilda-otsdb-dump` download data from an OpenTSDB endpoint.
* `kilda-otsdb-restore` restore data to an OpenTSDB capable database.

## Build
- `make prod` builds a container image of the application and label it with kilda-otsdb-dump-restore.
- `make devel` builds a container and start an interactive shell inside it, allowing the app directory to be attached as a volume inside the container.
This means that any changes made to the application code will be available inside the container without the need for a restart or rebuild, making it a useful tool for debugging.

## Usage
The tool is inside a Docker container. To use the it, run the following command in your terminal:
```
docker run kilda-otsdb-dump-restore <command_to_use>
```
Note: if you need to run it in localhost, you need to add the `--network="host"` docker flag.

### Dump data from OpenTSDB:
Use `kilda-otsdb-dump` command to dump data from an OpenTSDB.
```
Usage: kilda-otsdb-dump [OPTIONS] OPENTSDB_ENDPOINT TIME_START

  This tool dumps the data from an OpenTSDB

  OPENTSDB_ENDPOINT openTSDB endpoint

  TIME_START time since the data is dumped

  Example: kilda-otsdb-dump http://example.com:4242 2023-03-08

Options:
  --time-stop TIME_STOP       Timestamp where to stop dumping  [default: NOW]
  --dump-dir DIRECTORY        Location where dump files will be stored
  --query-frame-size INTEGER  OpenTSDB query time frame size (seconds)
                              [default: 180]
  --metrics-prefix TEXT       Only metrics that match this prefix will be
                              dumped  [default: kilda.]
  --remove-metadata
  --help                      Show this message and exit.
```

### Restore data to OpenTSDB:
Use `kilda-otsdb-restore` command to restore data previously dumped to an OpenTSDB.
```
Usage: kilda-otsdb-restore [OPTIONS] OPENTSDB_ENDPOINT

  This tool restore the data to an OpenTSDB

  OPENTSDB_ENDPOINT openTSDB endpoint

  Example: kilda-otsdb-restore http://example.com:4242

Options:
  --dump-dir DIRECTORY          Location where dump files are stored
  --request-size-limit INTEGER  Limit for "put" request payload size (bytes)
                                [default: 4096]
  --help                        Show this message and exit.
```
### Date and time formats
```
%Y-%m-%d, %Y-%m-%dT%H:%M:%S, %Y-%m-%d %H:%M:%S
```
- "2023-03-22"

- "2023-03-22T23:59:59"

- "2023-03-22 23:59:59"

### Example of use:
Scenario:
* One OpenTSDB service that has stored data.
* One empty VictoriaMetrics service with OpenTSDB capabilites.
* We want to migrate the data from the OpenTSDB to the VictoriaMetric service.

This could be a posible workflow:
1. Create volume to store data
```bash
docker volume create opentsdb-data
```
2. Next, we need to dump the data from OpenTSDB. The following command gets the data from a specified time until now and save it to disk:
```bash
docker run --rm -v opentsdb-data:/tmp kilda-otsdb-dump-restore kilda-otsdb-dump http://opentsdb:4242 "2023-03-08"
```
3. After dumping the data, we can restore it using the following command:
```bash
docker run --rm -v opentsdb-data:/tmp kilda-otsdb-dump-restore kilda-otsdb-restore http://victoria:4242
```
4. Finally, we can remove the volume using the following command:
```bash
docker volume remove opentsdb-data
```

Another approach would be to use a loop that iterates over a time range, such as days. This method could be beneficial when migrating a large amount of data.
1. Create volume to store data
```bash
docker volume create opentsdb-data
```
2. Next, we need to dump the data from OpenTSDB. Use the following command to get the data from a specified time until the --time-stop time and save it to disk:
```bash
docker run --rm -v opentsdb-data:/tmp kilda-otsdb-dump-restore kilda-otsdb-dump --time-stop "2023-03-22T11:00:00" http://opentsdb:4242 "2023-03-22T00:00:00"
```
3. After dumping the data, we can restore it using the following command:
```bash
docker run --rm -v opentsdb-data:/tmp kilda-otsdb-dump-restore kilda-otsdb-restore http://victoria:4242
```
4. Finally, we can remove the volume using the following command:
```bash
docker volume remove opentsdb-data
```
