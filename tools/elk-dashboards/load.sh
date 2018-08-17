#!/bin/bash
ELASTICSEARCH=http://localhost:9200
CURL=curl
KIBANA_INDEX=".kibana"

(echo > /dev/tcp/127.0.0.1/9200) >/dev/null 2>&1
 result=$?
if [[ $result -eq 0 ]]; then

echo "Loading index-pattern"
curl -X PUT --data @index_pattern.json -H 'Content-Type: application/json' 'http://localhost:9200/.kibana/index-pattern/AWUfJAj0duClWTXkEj7q'
echo

echo "Loading dashboards to ${ELASTICSEARCH} in ${KIBANA_INDEX}"

for file in search/*.json
do
    ID=$(basename ${file} .json | awk -F"_" '{print $2}' | awk -F"." '{print $1}')
    echo "Loading search ${ID}:"
    ${CURL} -XPUT -H 'Content-Type: application/json' ${ELASTICSEARCH}/${KIBANA_INDEX}/search/${ID} \
        -d @${file} || exit 1
    echo
done

for file in visualization/*.json
do
    ID=$(basename ${file} .json | awk -F"_" '{print $2}' | awk -F"." '{print $1}')
    echo "Loading visualization ${ID}:"
    ${CURL} -XPUT -H 'Content-Type: application/json' ${ELASTICSEARCH}/${KIBANA_INDEX}/visualization/${ID} \
        -d @${file} || exit 1
    echo
done

for file in dashboard/*.json
do
    ID=$(basename ${file} .json | awk -F"_" '{print $2}' | awk -F"." '{print $1}')
    echo "Loading dashboard ${ID}:"
    ${CURL} -XPUT -H 'Content-Type: application/json' ${ELASTICSEARCH}/${KIBANA_INDEX}/dashboard/${ID} \
        -d @${file} || exit 1
    echo
done
else
   echo "elasticsearch is not available from host system... skipping..."
fi

