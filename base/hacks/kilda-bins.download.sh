#!/usr/bin/env bash

# ==•==•==•==•==•==•==•==
# This script downloads essential files for building the development containers.
# It'll only do this if the files don't already exist.
#
# NB:
#   • if you set DEBUG=1, extra comments will appear
# ==•==•==•==•==•==•==•==

BASE=`dirname $0`
SOURCE=${SOURCE:-${BASE}/kilda-bins.requirements.txt}
TARGET=${TARGET:-${BASE}/../../kilda-bins}
RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color
Bad_Count=0
Download_Attempts=2

debug() {
  if [[ "${DEBUG}" == "1" ]]; then
    $@
  fi
}

## eliminate text after the ':'
#strip_latest() {
#  local result=()
#  for plugin in $@
#  do
#    result+="${plugin%%:?*} "
#  done
#  echo ${result[@]}
#}



verify_file() {
    file=$1
    md5_file=$2
    md5_expected=`cat ${md5_file} | tr -d '[:space:]' | tr '[:upper:]' '[:lower:]'`
    md5_actual=`${md5} ${file} | tr '[:upper:]' '[:lower:]' | sed -e 's/ .*$//'`
    if [[ ${md5_expected} == ${md5_actual} ]] ; then
        echo -e  " ... ${GREEN} SUCCESFUL MD5 Match ${NC}"
    else
        Bad_Count=$((Bad_Count + 1))
        echo -e " ... ${RED} FAILED MD5 Match... DELETING ${NC}"
        rm -rf ${file}
    fi
    debug echo "md5_expected = ${md5_expected}"
    debug echo "md5_actual = ${md5_actual}"
}


download_file() {
    url=$1
    dest=$2
    echo "FETCH...: ${url} --> ${dest}"

    curl --connect-timeout ${CURL_CONNECTION_TIMEOUT:-60} \
          --retry ${CURL_RETRY:-10} \
          --retry-delay ${CURL_RETRY_DELAY:-5} \
          --retry-max-time ${CURL_RETRY_MAX_TIME:-240} \
          -s -f -L "$url" -z "${dest}" -o "${dest}" &
}

# pass in all downloads, and this will download *if newer*
download_latest() {
  local url dest
  local Download_Count=0
  mkdir -p ${TARGET}
  for url in $@
  do
    file_url="${url%%:::*}"
    file_name="${file_url##*/}"
    file_dest="${TARGET}/${file_name}"

    # First check to see if it exists and is a good file (eg not truncated)
    if [[ ! -e ${file_dest} ]] ; then
        Download_Count=$((Download_Count + 1))
        download_file ${file_url} ${file_dest}
    fi
  done

  if (( Download_Count > 0 )) ; then
    echo -n "WAITING for all downloads to finish... "
    wait
    echo "DONE"
  fi

  for url in $@
  do
    file_url="${url%%:::*}"
    file_name="${file_url##*/}"
    file_dest="${TARGET}/${file_name}"
    md5_dest="${TARGET}/${file_name}.md5"

    echo -n "....MD5 TEST: ${file_dest}"
    verify_file ${file_dest} ${md5_dest}
  done

  echo "DONE"
}

check_deps() {
  $(which curl &>/dev/null)
  if (( $? != 0 )) ; then
    echo -e "${RED} Please install curl, this build requires it ${NC}"
    exit 1
  fi

  $(which md5 &>/dev/null)
  if (( $? != 0 )) ; then
    $(which md5sum &>/dev/null)
    if (( $? != 0 )) ; then
      echo -e "${RED} Please install md5 or md5sum, this build requires it ${NC}"
      exit 1
    else
      md5='md5sum'
    fi
  else
    md5='md5 -q'
  fi
}

main() {
  check_deps

  # 1) Get the names of the files
  FILES=`cat ${SOURCE} | grep --invert-match "^#" | tr '\n' ' '`
  debug echo "FILES: " ${FILES}

  # 2) Download files
  FILE_COUNT=`echo ${FILES} | wc -w`
  debug echo "Count: " ${FILE_COUNT}

  while true ; do
    if (( Download_Attempts < 1 )) ; then
      echo "Exceeded download attempts"
      break
    fi

    echo "Download Attempts Remaining: ${Download_Attempts}"
    Download_Attempts=$((Download_Attempts - 1))

    download_latest ${FILES}
    if (( Bad_Count > 0 )) ; then
      echo "Found ${Bad_Count} failed md5 checksums .. running again to validate"
    else
      break
    fi
    Bad_Count=0
  done
}

main "$@"
