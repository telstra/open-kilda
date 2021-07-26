export:
        cd /opt/derby/derby-script-files && ./derbymysqlmigration.sh

import:
        cd /opt/derby/derby-script-files && ./derby-mysql-import.sh


pre-req:
         mkdir /opt/derby && mkdir /opt/derby/derby-script-files/  && cp -r script-files/* /opt/derby/derby-script-files/
         sed -i "s;<oldDb>;$(olddb);" "/opt/derby/derby-script-files/derby.properties" &&  sed -i "s;<newDb>;$(newdb);" "/opt/derby/derby-script-files/derby.properties" &&  sed -i "s;<derbyDbPath>;$(derbydb);" "/opt/derby/derby-script-files/derby.properties"
