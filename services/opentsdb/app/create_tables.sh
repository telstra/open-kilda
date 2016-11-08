#!/bin/bash

TSDB_TABLE=${TSDB_TABLE-'tsdb'}
UID_TABLE=${UID_TABLE-'tsdb-uid'}
TREE_TABLE=${TREE_TABLE-'tsdb-tree'}
META_TABLE=${META_TABLE-'tsdb-meta'}
BLOOMFILTER=${BLOOMFILTER-'ROW'}
COMPRESSION=${COMPRESSION-'LZO'}
COMPRESSION=`echo "$COMPRESSION" | tr a-z A-Z`
TABLES="$TSDB_TABLE $UID_TABLE $TREE_TABLE $META_TABLE"

test -n "$HBASE_HOME" || {
  echo >&2 'The environment variable HBASE_HOME must be set'
  exit 1
}
test -d "$HBASE_HOME" || {
  echo >&2 "No such directory: HBASE_HOME=$HBASE_HOME"
  exit 1
}

hbh=$HBASE_HOME
unset HBASE_HOME

case $COMPRESSION in
  (NONE|LZO|GZIP|SNAPPY)  :;;  # Known good.
  (*)
    echo >&2 "warning: compression codec '$COMPRESSION' might not be supported."
    ;;
esac

function create_table {
  case $1 in
  $UID_TABLE)
    echo "Creating $TSDB_TABLE"
    exec "$hbh/bin/hbase" shell <<-EOF
      create '$UID_TABLE',
        {NAME => 'id', COMPRESSION => '$COMPRESSION', BLOOMFILTER => '$BLOOMFILTER'},
        {NAME => 'name', COMPRESSION => '$COMPRESSION', BLOOMFILTER => '$BLOOMFILTER'}
EOF
    ;;
  $TSDB_TABLE | $TREE_TABLE)
    echo "Creating $table"
    exec "$hbh/bin/hbase" shell <<-EOF
      create '$table',
        {NAME => 't', VERSIONS => 1, COMPRESSION => '$COMPRESSION', BLOOMFILTER => '$BLOOMFILTER'}
EOF
    ;;
  $META_TABLE)
    echo "Creating $META_TABLE"
    exec "$hbh/bin/hbase" shell <<-EOF
      create '$META_TABLE',
        {NAME => 'name', COMPRESSION => '$COMPRESSION', BLOOMFILTER => '$BLOOMFILTER'}
EOF
    ;;
  esac
}

for table in $TABLES
do
  echo "exists '$table'"
  echo "exists '$table'" | /opt/hbase/bin/hbase shell | grep "Table $table does not exists" &> /dev/null
  echo result is $?
  if [ $? == 1 ]
  then
    echo $table does not exist
    #create_table $table
  fi
done
