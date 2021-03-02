#!/bin/bash

graphml_file=$1

cp ${graphml_file}{,.bak}

# Replace flow_event, flow_history, flow_dump, port_history labels with *_copy.
sed -i -E -e "s/(<node[^>]*labels=\"\:[^>]*)\"/\1\_copy\"/" \
          -e "s/(<node[^<]*<data key=\"labels\">:[^<]*)</\1_copy</" ${graphml_file}
