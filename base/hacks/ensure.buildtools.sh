#!/usr/bin/env bash

# Make sure the basics of the build system are available
apt-get update
apt-get install -y --no-install-recommends apt-utils
apt-get install -y make patch rsync
