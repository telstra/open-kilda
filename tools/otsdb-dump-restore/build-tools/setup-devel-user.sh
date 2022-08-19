#!/bin/sh

set -eux

echo "$@"

DEVEL_NAME="${1}"
DEVEL_UID="${2}"
DEVEL_GID="${3}"

if [ ${DEVEL_UID} -eq 0 ]; then
  # nothing to do for root user
  exit
fi

if [ -z "$(getent group "${DEVEL_NAME}")" ]; then
  groupadd -g "${DEVEL_GID}" "${DEVEL_NAME}"
fi
if [ -z "$(getent passwd "${DEVEL_NAME}")" ]; then
  useradd -m -u "${DEVEL_UID}" -g "${DEVEL_GID}" -s /bin/bash "${DEVEL_NAME}"
fi

chown "${DEVEL_UID}:${DEVEL_GID}" \
  /kilda \
  /usr/local \
  /usr/local/bin \
  /usr/local/lib/python3.10/site-packages
