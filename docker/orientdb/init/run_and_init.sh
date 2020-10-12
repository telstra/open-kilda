#!/bin/bash
  
set -m
  
# Start the primary process and put it in the background
/orientdb/bin/dserver.sh &

# Check and install netcat
if [ -f /usr/bin/ncat ]; then
    echo "/usr/bin/ncat is found"
else
    apt update
    apt -y install ncat
fi

# Start the initialization process
sleep 45
/orientdb/bin/console.sh /orientdb/init/create-db-with-schema.osql
sleep 45

# Open a port to indicate that the initialization process is completed
/usr/bin/ncat -k -v --listen 4444 &

# Bring the primary process back into the foreground
fg %1