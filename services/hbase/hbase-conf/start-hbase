#!/bin/bash

/opt/hbase/bin/hbase regionserver start > logregion.log 2>&1 &
/opt/hbase/bin/hbase master start --localRegionServers=0
./hbase-daemon.sh start rest
