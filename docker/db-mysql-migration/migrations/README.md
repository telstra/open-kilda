# MySQL migrations

## migrations concepts
* use `yaml` format
* do not include `changeSet` entries directly into `root.yaml` - create a separate migration file for each migration and
  include it into `root.yaml`
* put all request that must be applied as one transaction inside one `changeSet`
* prefer liquibase "change types" over plain SQL requests
* filename + changeSet id + author together represents a unique identifier for changeSet, used by `liquibase` to track
  the history of migration, so you `id` records must be unique for the migration file
* name migration files as `NNN-human-readable-description.yaml`, where `NNN` is a decimal digit with leading zeros (for
  natural alphanumeric file name sorting)
* add `tagDatabase` changeSet at the start of your migration change sets list, it will be used as pointer for rollback
  operations

## Examples

migration file `001-feature-ABC.yaml`
```yaml
databaseChangeLog:
  - changeSet:
      id: some-id
      author: UNKNOWN
      changes:
        - sql: "INSERT ..."
      rollback:
        - sql: "DELETE ..."
```

chunk into `root.yaml`
```yaml
  - include:
      relativeToChangelogFile: true
      file: 001-feature-ABC.yaml
```

Tag for rollback operation (during rollback everything that was applied after this tag will be rolled back)
```yaml
changeSet:
  id: tag-for-some-migration
  author: UNKNOWN
  changes:
  - tagDatabase:
      tag: 000-migration
```

To start DB update manually you need to compose a migration image and execute a migration script. Optionally, you
can execute liquibase with arbitrary parameters.

To create an image, navigate to the root of the OpenKilda project and execute:
```shell script
docker compose build db_mysql_migration
```

For executing a migration script (you can override other environment variables as well). `NO_SLEEP` parameter will exit the
script normally, otherwise it will sleep infinitely to preserve the container running:
```shell script
docker run --volume=/etc/resolv.conf:/etc/resolv.conf --rm --network=host \
-e KILDA_MYSQL_JDBC_URL="jdbc:mysql://localhost:8101/kilda" \
-e NO_SLEEP=true \
--entrypoint=/kilda/migrate-develop.sh \
kilda/db_mysql_migration:latest
```

For executing liquibase manually, for example for rolling back changes up to some specific tag, execute the following command:
```shell script
docker run \
  --volume=/etc/resolv.conf:/etc/resolv.conf --rm --network=host \
  kilda/db_mysql_migration:latest \
  --username="kilda" \
  --password="kilda" \
  --url="jdbc:mysql://localhost:8101/kilda" \
  rollback --changelog-file="root.yaml" --tag="some-specific-tag"
```
