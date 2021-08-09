#!/bin/bash

cd -- "$(dirname -- "$0")" || exit 1

cid=build/devel.cid
make "${cid}" \
    && exec docker start -ai "$(cat ${cid})"
