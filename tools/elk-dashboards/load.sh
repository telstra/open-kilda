#!/bin/bash


ELASTICSEARCH=http://localhost:9200
CURL=curl

KIBANA_INDEX=".kibana"


KIBANA_ADDR="http://127.0.0.1:5601"
KIBANA_INDEX_PATTERN_ENDPOINT="/api/index_patterns/index_pattern"

(echo > /dev/tcp/127.0.0.1/9200) >/dev/null 2>&1
 result=$?
if [[ $result -eq 0 ]]; then

echo "Loading index-pattern"
curl \
    -X POST \
    --data @index_pattern.json \
    -H 'Content-Type: application/json' \
    -H "kbn-xsrf: true" \
    ${KIBANA_ADDR}${KIBANA_INDEX_PATTERN_ENDPOINT}

echo "Loading dashboards to ${KIBANA} in ${KIBANA_INDEX_PATTERN_ENDPOINT}"

exit

# TODO: saved object are not compatible with ELK 7.x
# and should be recreated.

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
