#!/bin/bash

source derby.properties

if [[ -z $oldDB || -z $newDB || -z $derbyDbPath ]]; then
  echo 'one or more variables are undefined in derby.propeties file'
  exit 1
fi

echo "oldDB is: $oldDB"
echo "newDB is: $newDB"
echo "derbyDbPath is: $derbyDbPath"


read -p "Please Confirm The Details of derby.properties file!! (y/n) " RESP
if [ "$RESP" = "y" ]; then
  echo "Glad to hear it"
else
  exit 0
fi


cd /opt/derby

if [ ! -d "/opt/derby/db-derby-10.14.2.0-bin/" ]; then
wget https://mirrors.estointernet.in/apache/db/derby/db-derby-10.14.2.0/db-derby-10.14.2.0-bin.zip
unzip db-derby-10.14.2.0-bin.zip
 cd db-derby-10.14.2.0-bin/
fi

mkdir -p /opt/derby/derby-data
mkdir -p /opt/derby/derby-metadata
cd /opt/derby/derby-script-files

sed -i "s;##old-db-name##;$oldDB;" "/opt/derby/derby-script-files/derby-export.sql"
sed -i "s;##derby-db-path##;$derbyDbPath;" "/opt/derby/derby-script-files/input_file.txt"
java -jar /opt/derby/db-derby-10.14.2.0-bin/lib/derbyrun.jar ij input_file.txt > /tmp/export-data.log
