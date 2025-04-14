#!/bin/bash

images=("db-migration" "db-mysql-migration")

for image in "${images[@]}"; do
  echo "Start downloading library dependencies for $image ..."
  sh "$image/libs.sh"
  mkdir -p libs/$image/ && cp lib/* libs/$image/ && rm -rf lib
  mkdir -p $image/lib/ && cp libs/$image/* $image/lib
  echo "Finished downloading library dependencies for $image !!! OK"
done
rm -rf libs

