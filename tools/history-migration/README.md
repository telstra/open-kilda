# History migration tools

This packet contains python package that provide `kilda-history-migration` CLI tool. The tool itself
provides following commands:
* pull-mysql
* pull-orientdb
* push-mysql
* push-orientdb

`pull-*` commands required to download data from DB and store it into `ndjson` file.

`push-*` commands required to upload data from `ndjson` file into database.

Type of database noted into command suffix. Each command accept required for corresponding database set of options (
login, password, hostname etc). List of possible options can be obtained using `--help` CLI option. For example:
```bash
kilda-history-migration pull-orientdb --help
Usage: kilda-history-migration pull-orientdb [OPTIONS] DATABASE STREAM_OUT

Options:
  --orient-db-host TEXT
  --orient-db-port INTEGER
  --orient-db-login TEXT
  --orient-db-password TEXT
  --time-start [%Y-%m-%d|%Y-%m-%dT%H:%M:%S|%Y-%m-%d %H:%M:%S]
  --time-stop [%Y-%m-%d|%Y-%m-%dT%H:%M:%S|%Y-%m-%d %H:%M:%S]
  --help                          Show this message and exit.
```

In addition to the database access related options, `pull-*` commands accepts `--time-start` and `--time-stop` 
options. These options can define time range (in local timezone), limited by one or by two sides, to download data. 
Using these time range ability it is possible to make incremental dump/restore operation. 

It is safe to make upload data `push-*` several times using same stored dump (already existed records in DB will not be
overwritten).

There are several possible approaches to migrate data (lets suppose we are going to migrate data from `orientdb`
into `mysql`).
1. Full backup / restore in one chunk (require kilda system shutdown while pull/push are going operations).
1. Switch kilda settings from `orientdb` to `mysql` (i.e. make kilda restart on clean target db), after kilda
   restart perform `pull-orient` / `push-mysql`.
1. copy existing data till some time (now) using `pull-orient --time-stop=$now` + `push-mysql`, switch kilda settings 
   to the `mysql`, copy possible remaining data using `pull-orient --time-start=$now` + `push-mysql`.
