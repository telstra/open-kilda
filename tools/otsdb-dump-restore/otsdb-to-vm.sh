#!/bin/bash

# shellcheck enable=require-variable-braces

#
# Script for migration data from OpenTSDB to Victoria metrics
# there are two processes:
#  dump - which is saving batches of data from OpenTSDB to the specified folder
#  restore - which is uploading data dumped data to the remote Victoria metrics server
#  dump process running batches in the loop
#    every time it checking if there are enogh of space/inodes to make a dump
#    if lack of space detected - dump loop will wait for a free space
#  restore process running in the background and writing execution process to the log file 'background_log'
#    every loop only one folder is processed
#    if there is no new folder to process it will suspend execution and wait for a new folder to restore
#    after successfull restore - dump batch folder will be removed and free space can be used by dump process
#  once dump process will finish all batches it will send SIGUSR1 signal to the restore process, and will wait until restore process will be finished
#  on SIGUSR1 signal restore process will understand whel all folders will be processed and removed - it will finish execution.
#  Ctrl+C pressed once doesn't do something, just increase the counter
#  Pressed twice - will stop dump process. process will wait for a finish background restore task
#  Pressed third time and more will send stop signal to background process
#

# enable job control
set -m

# ==============================================================================
# set defaults
interval="day"
interval_format="%Y-%m-%d"
concurrent_jobs_limit="50"
concurrent_jobs="1"
query_frame_size="180"
dump_folder_prefix="opentsdb-data-"
dump_storage_folder="/vm_migration_volume"
default_docker_image_name='kilda-otsdb-dump-restore'
ready_suffix='_dump_done'
# if file exist - background process will continue execution
marker_for_execution="${marker_for_execution:-/tmp/kilda-otsdb-dump-restore-marker}"
background_log="${background_log:-/tmp/kilda-otsdb-dump-restore.log}"
# interval (sec) for upload loop to wait new ready folder with data to process
delay_between_checks_new_folder="${delay_between_checks_new_folder:-10}"
# under min_inodes limit dump will wait to freeup inodes (value depends on your situation)
min_inodes="${min_inodes:-1000}"
# under min_free_space (bytes) dump will wait to freeup space (value depends on size of your dumps)
min_free_space="${min_free_space:-100000000}"
# timeout to get info from src endpoint (used by curl which is checking mertics presence)
src_timeout="10"
# ==============================================================================
# shellcheck disable=SC2155
readonly _c_none_=$(printf '\e[39m')
# shellcheck disable=SC2155
readonly _c_red_=$(printf '\e[31m')
# shellcheck disable=SC2155
readonly _c_green_=$(printf '\e[32m')
# shellcheck disable=SC2155
readonly _c_yellow_=$(printf '\e[33m')
# shellcheck disable=SC2155
readonly _c_blue_=$(printf '\e[34m')
# shellcheck disable=SC2155
readonly _c_magenta_=$(printf '\e[35m')

# ==============================================================================
function log() {
    local l_type="${1^^}"
    local l_value
    case "${l_type}" in
      OK)
        l_value="${_c_green_}[${l_type}]${_c_none_}"
      ;;
      INFO)
        l_value="${_c_blue_}[${l_type}]${_c_none_}"
      ;;
      WARNING)
        l_value="${_c_yellow_}[${l_type}]${_c_none_}"
      ;;
      ERROR|FAIL)
        l_value="${_c_red_}[${l_type}]${_c_none_}"
      ;;
      *)
        # magenta for unknown
        l_value="${_c_magenta_}[${l_type}]${_c_none_}"
      ;;
    esac
    # print params starting from the second
    echo -e "${l_value} $("${date_cmd}" "+%F %T") ${*:2}"
}

# ==============================================================================
# shellcheck disable=SC2155
readonly date_cmd="$(which date)"
# shellcheck disable=SC2155
readonly bc_cmd="$(which bc)"

if [ -z "${date_cmd}" ] ; then
    log "ERROR" "date command not found" >&2
    exit 1
fi

if [ -z "${bc_cmd}" ] ; then
    log "ERROR" "bc command not found" >&2
    exit 1
fi

# ==============================================================================
function help() {
    log "INFO" "Allowed options are:"
    log "INFO" "  -s|--source|--opentsdb_endpoint"
    log "INFO" "                             openTSDB endpoint"
    log "INFO" "  -d|--destination|--victoria_metrics_endpoint"
    log "INFO" "                             victoria metrics endpoint"
    log "INFO" "  -sd|--start_date           time since the data is dumped"
    log "INFO" "  -ed|--end_date             time where to stop dumping"
    log "INFO" "  -m|--metrics_prefix        prefix for exported metrics"
    log "INFO" "  -i|--interval              time frame size to dump data. <day|hour> Default is day."
    log "INFO" "  -c|--concurrent_jobs       number of concurrent jobs to run. Default is 1"
    log "INFO" "  -q|--query_frame_size      number of data points to query from OpenTSDB"
    log "INFO" "                             at once. Default is 180."
    log "INFO" "  -dfp|--dump_folder_prefix  prefix string used for a folder name"
    log "INFO" "  -dsf|--dump_storage_folder path string to storage where the dump folders will be stored"
    log "INFO" "  -sbl|--show_background_log option used to display tail on the background log"
    log "INFO" "Examples:"
    log "INFO" "  ./otsdb-to-vm.sh -s http://opentsdb.example.com:4242 -d http://victoria-metrics.example.com:4242 --start_date 2022-01-01 --end_date 2022-01-31 --metrics_prefix 'kilda.' --interval day"
    log "INFO" "  ./otsdb-to-vm.sh -s http://opentsdb.example.com:4242 -d http://victoria-metrics.example.com:4242 --start_date 2022-01-01T00:00:00 --end_date 2022-01-01T23:59:59 --metrics_prefix 'kilda.' --interval hour"
}

# ==============================================================================
function check_metrics_exist() {
    local l_opentsdb_endpoint="${1}"
    local l_metrics_prefix="${2}"
    local l_json
    l_json=$(curl -m"${src_timeout}" -s "${l_opentsdb_endpoint}/api/suggest?type=metrics&q=${l_metrics_prefix}&max=150" 2>/dev/null)
    local l_exitcode="${?}"
    if [ "${l_exitcode}" -ne "0" ] ; then
        log "ERROR" "curl returned \"${_c_red_}${l_exitcode}${_c_none_}\" error." >&2
        log "ERROR" "check man page with: ${_c_yellow_}man --pager='less -p \"^EXIT CODES\"' curl${_c_none_}"
        return 1
    fi
    # I heard about jq but ... not present everywhere by default
    # remove spaces and newlines then remove square brackets from begin and end of json array then replace commas with newlines
    local l_result
    l_result=$(tr -d ' \n' <<<"${l_json}" | sed 's/^\[\|\]$//g;s/","/"\n"/g' | sed 's/^"\|"$//g') #'
    # if result isn't empty - success. mertics exist
    if [ -z "${l_result}" ] ; then
        return 1
    else
        log "INFO" "\"$(wc -l <<<"${l_result}")\" metrics was/were found"
        return 0
    fi
}

# ==============================================================================
while [ "${#}" -gt "0" ] ; do
  key="${1}"
  case "${key}" in
    -s|--source|--opentsdb_endpoint)
      opentsdb_endpoint="${2}"
      shift # remove key
      shift # remove value
    ;;

    -d|--destination|--victoria_metrics_endpoint)
      victoria_metrics_endpoint="${2}"
      shift # remove key
      shift # remove value
    ;;

    -sd|--start_date)
      start_date="${2}"
      shift # remove key
      shift # remove value
    ;;

    -ed|--end_date)
      end_date="${2}"
      shift # remove key
      shift # remove value
    ;;

    -m|--metrics_prefix)
      # check if after prefix we have another option
      if [[ "${2}" =~ ^(-.|--).+$ ]] ; then
        metrics_prefix=""
        shift # remove key
      else
        metrics_prefix="${2}"
        shift # remove key
        shift # remove value
      fi
    ;;

    -i|--interval)
      interval="${2,,}"
      # set time interval
      if [ "${interval}" == "hour" ] ; then
        interval_format="%Y-%m-%dT%H:00:00"
        increment="1 hour"
      elif [ "${interval}" == "day" ] ; then
        interval_format="%Y-%m-%d"
        increment="1 days"
      else
        log "ERROR" "Invalid interval: \"${interval}\"" >&2
        log "ERROR"  "Allowed interval: day|hour" >&2
        exit 1
      fi
      shift # remove key
      shift # remove value
    ;;

    -c|--concurrent_jobs)
      concurrent_jobs="${2}"
      if [[ ! "${concurrent_jobs}" =~ ^[0-9]+$ ]] ; then
        log "ERROR" "Concurency value should be a number [1..${concurrent_jobs_limit}]" >&2
        exit 1
      fi
      if [ "${concurrent_jobs}" -lt "1" ] || [ "${concurrent_jobs}" -gt "${concurrent_jobs_limit}" ] ; then
        log "ERROR" "Concurency value \"${concurrent_jobs}\" is out of range [1..${concurrent_jobs_limit}]" >&2
        exit 1
      fi
      shift # remove key
      shift # remove value
    ;;

    -q|--query_frame_size)
      query_frame_size="${2}"
      shift # remove key
      shift # remove value
    ;;

    -dfp|--dump_folder_prefix)
      dump_folder_prefix="${2}"
      shift # remove key
      shift # remove value
    ;;

    -dsf|--dump_storage_folder)
      dump_storage_folder=$(realpath "${2}")
      exitcode="${?}"
      if [ "${exitcode}" -ne "0" ] ; then
        log "ERROR" "Can't resolv real path for \"${2}\". Abort." >&2
        exit 1
      fi
      shift # remove key
      shift # remove value
    ;;

    -sbl|--show_background_log)
      show_background_log="yes"
      shift
    ;;
    *)
      log "ERROR" "Unknown option \"${key}\"" >&2
      help
      exit 1
    ;;
  esac
done

# check if mandatory vars are all defined
while read -r var_name ; do
    if [ -z "${!var_name+x}" ] ; then
        log "ERROR" "You have to define --${var_name} option" >&2
        _error_="1"
    fi
done < <(echo -e "opentsdb_endpoint\nvictoria_metrics_endpoint\nstart_date\nend_date")
# exit if there was an error
if [ -n "${_error_}" ] ; then
    help
    exit 1
fi

# check if dump storage folder exist
if [ ! -d "${dump_storage_folder}" ] ; then
    log "ERROR" "Folder \"${dump_storage_folder}\" for saving dumps must exist. Need to create or define with option --dump_storage_folder" >&2
    exit 1
fi

# check if docker installed and you have access
result=$(docker --version 2>&1)
exitcode="${?}"
if [ "${exitcode}" -ne "0" ] ; then
    log "ERROR" "Can't execute docker command. Docker may be not installed or user \"${_c_yellow_}$(whoami)${_c_none_}\" require access to the docker socket." >&2
    log "ERROR" "${result}" >&2
    exit 1
fi

if [ -z "${DEBUG}" ] ; then
# Check if image exist
if [ -z "$(docker images -q "${default_docker_image_name}" 2> /dev/null)" ]; then
    log "ERROR" "Docker image \"${_c_yellow_}${default_docker_image_name}${_c_none_}\" not found. Please build it first." >&2
    exit 1
fi
fi

# check if we have metrics to dump
if ! check_metrics_exist "${opentsdb_endpoint}" "${metrics_prefix}" ; then
    log "ERROR" "No metrics found by prefix \"${_c_yellow_}${metrics_prefix}${_c_none_}\" at \"${_c_magenta_}${opentsdb_endpoint}${_c_none_}\""
    exit 1
fi

# convert start and end date to interval format
start_date="$(${date_cmd} -d "${start_date}" +${interval_format})"
end_date="$(${date_cmd} -d "${end_date}" +${interval_format})"

# ==============================================================================
# Define function to dump data from OpenTSDB
function dump_data {
    local l_start_date="${1}"
    local l_metrics_prefix="${2}"
    local l_interval_end_date="${3}"
    local l_opentsdb_endpoint="${4}"
    local l_concurrent_jobs="${5}"
    local l_query_frame_size="${6}"
    local l_volume_suffix l_local_bind_folder
    # replace `-` and `T` to `.` and `_` for a filename's suffix
    l_volume_suffix=$(sed 'y/-T/._/' <<< "${l_start_date}_${l_interval_end_date}")
    l_local_bind_folder="${dump_storage_folder}/${dump_folder_prefix}${l_volume_suffix}"
    if [ -e "${l_local_bind_folder}${ready_suffix}" ] ; then
        log "ERROR" "Folder \"${l_local_bind_folder}${ready_suffix}\" exist already. Not required to dump again." >&2
        return 1
    fi
    log "INFO" "Dumping data since \"${l_start_date}\" till \"${l_interval_end_date}\" to folder \"${l_local_bind_folder}\""
    # Create dump folder for this iteration
    if [ ! -d "${l_local_bind_folder}" ] ; then
        mkdir -p "${l_local_bind_folder}"
    else
        log "WARNING" "Folder \"${l_local_bind_folder}\" exist already. Skip creation." >&2
    fi
if [ -z "${DEBUG}" ] ; then
    docker run --rm --network="host" \
      -v "${l_local_bind_folder}":/tmp \
      "${default_docker_image_name}" \
      kilda-otsdb-dump \
        --query-frame-size "${l_query_frame_size}" \
        --concurrent "${l_concurrent_jobs}" \
        --metrics-prefix "${l_metrics_prefix}" \
        --time-stop "${l_interval_end_date}" \
        "${l_opentsdb_endpoint}" "${l_start_date}"
else
    delay=2
    log "DEBUG" "Sleep ${delay} in dump" >&2
    sleep "${delay}"
fi
    local exicode="${?}"
    if [ "${exicode}" -ne "0" ] ; then
        log "ERROR" "Failed to dump data into \"${_c_yellow_}${l_local_bind_folder}${_c_none_}\"" >&2
        log "ERROR" "Removing folder \"${_c_yellow_}${l_local_bind_folder}${_c_none_}\"" >&2
        [ -d "${l_local_bind_folder}" ] && rm -rf "${l_local_bind_folder}"
        log "ERROR" "Lost interval \"${l_start_date}\" till \"${l_interval_end_date}\" to folder \"${l_local_bind_folder}\"" >&2
    else
        log "INFO" "Rename dumped folder to inform restore process for upload."
        log "INFO" "\"${l_local_bind_folder}\" to \"${l_local_bind_folder}${ready_suffix}\"" >&2
        if [ -e "${l_local_bind_folder}${ready_suffix}" ] ; then
            log "ERROR" "Folder \"${l_local_bind_folder}${ready_suffix}\" exist already!" >&2
            log "ERROR" "Leave folder \"${l_local_bind_folder}\" as it is. Check it manually." >&2
            exitcode=1
        else
            mv "${l_local_bind_folder}" "${l_local_bind_folder}${ready_suffix}"
        fi
    fi
    return "${exicode}"
}

# ==============================================================================
# Define function to restore data to Victoria Metrics
function restore_data {
    local l_victoria_metrics_endpoint="${1}"
    local l_local_bind_folder="${2}"
    log "INFO" "Restoring data from \"${l_local_bind_folder}\" to \"${l_victoria_metrics_endpoint}\"" >> "${background_log}"
if [ -z "${DEBUG}" ] ; then
    docker run --rm --network="host" \
      -v "${l_local_bind_folder}":/tmp \
      "${default_docker_image_name}" \
      kilda-otsdb-restore --request-size-limit 1048576 "${l_victoria_metrics_endpoint}"
else
    delay=5
    log "DEBUG" "Sleep ${delay} in restore" >> "${background_log}"
    sleep "${delay}"
fi
    local exicode="${?}"
    if [ "${exicode}" -ne "0" ] ; then
        log "ERROR" "Failed to restore data to Victoria Metrics" >> "${background_log}"
    else
        {
          log "INFO" "Restore for \"${l_local_bind_folder}\" successfully finished."
          log "INFO" "Remove folder \"${_c_yellow_}${l_local_bind_folder}${_c_none_}\""
          rm -rf "${l_local_bind_folder}"
          exitcode="${?}"
          [ "${exitcode}" -ne "0" ] && log "ERROR" "Failed to remove folder \"${l_local_bind_folder}\""
        } >> "${background_log}"
    fi
    return "${exicode}"
}


# ============================================================================== setup Ctrl+C handler for a restore
function exit_restoreloop() {
    # increment keypress counter
    ((stop_restore_loop++))
    {
      # newline
      echo
      log "WARNING" "Ctrl+C catched \"${stop_restore_loop}\" time(s)"
      log "WARNING" "Finishing restore loop"
    } >> "${background_log}"
}

# ============================================================================== restore loop function
function restore_loop() {
    stop_restore_loop=0
    dump_was_finished=0
    # setup trap handler for a background process
    trap exit_restoreloop SIGINT
    # setup trap handler for finished dumps. there will be no more new dumps. need to finish all we have and stop process.
    trap 'dump_was_finished=1' SIGUSR1

    log "INFO" "Restore loop process started" >> "${background_log}"
    local l_folder_to_process
    while [ -e "${marker_for_execution}" ] && [ "${stop_restore_loop}" -eq "0" ] ; do
        # take the first line sorted by date folders list with specified by regex mask
        l_folder_to_process=$(find "${dump_storage_folder}" -mindepth 1 -maxdepth 1 -type d \
           -regex '^'"${dump_storage_folder}"'/'"${dump_folder_prefix}"'.+'"${ready_suffix}"'$' \
           -printf "%TFT%TT\t%p\n" | sort | head -n1 | cut -d$'\t' -f2)

        # if folder with data to process is not found
        if [ -z "${l_folder_to_process}" ] ; then
            # and if dump was finished - exit the loop. work done.
            [ -n "${dump_was_finished}" ] && break
            log "INFO" "No folder to process. Sleep for a \"${delay_between_checks_new_folder}\" seconds" >> "${background_log}"
            sleep "${delay_between_checks_new_folder}"
            continue
        fi
        # run dump
        restore_data "${victoria_metrics_endpoint}" "${l_folder_to_process}"
    done
    [ ! -e "${marker_for_execution}" ] && log "INFO" "Exit from loop because of marker file not exist anymore \"${marker_for_execution}\"" >> "${background_log}"
    log "INFO" "Restore loop process finished" >> "${background_log}"
}




# ============================================================================== setup Ctrl+C handler for a dump
function exit_dumploop() {
    # increment count of keypres
    ((stop_dump_loop++))
    # print newline
    echo >&2
    log "WARNING" "Ctrl+C pressed \"${stop_dump_loop}\" time(s)" >&2
    if [ -z "${dump_status}" ] ; then
        if [ -n "${stop_dump_loop}" ] && [ ! "${stop_dump_loop}" -le "1" ] ; then
            log "WARNING" "Finishing dump loop" >&2
        else
            log "WARNING" "once - ignoring..." >&2
        fi
    fi
    # if we press 3 times and more - we send Ctrl+C to background process
    if [ "${stop_dump_loop}" -ge "3" ] ; then
        log "WARNING" "Third and more Ctrl+C sending to the background process \"${background_process_pid}\"" >&2
        local l_result
        if ! l_result=$(kill -SIGINT "${background_process_pid}" 2>&1) ; then
            log "WARNING" "kill returned error: \"${l_result}\"" >&2
        fi
    else
        log "WARNING" "(${stop_dump_loop}/3) Third and more Ctrl+C will be send to background process" >&2
    fi
}
stop_dump_loop=0
trap exit_dumploop SIGINT

# create the marker file
# while this file exist - background process will wait for a new folders to upload
touch "${marker_for_execution}"

# ============================================================================== start background restore loop
restore_loop "${victoria_metrics_endpoint}" &
background_process_pid="${!}"
log "DEBUG" "background process pid:[${background_process_pid}]" >&2
log "DEBUG" "Current pid:[${$}]" >&2

# save pid and background pid - just to have ability to monitor outside
echo -e "${$}\t${background_process_pid}" > "${marker_for_execution}"

# ============================================================================== RUN MAIN DUMP LOOP
# Loop through dates
while [[ "${start_date}" < "${end_date}" ]] && [ "${stop_dump_loop}" -le "1" ] ; do
    take_next_dump=""
    # take free inodes and disk space
    read -r free_inodes free_space < <(awk '$1~/^[0-9]+$/{print $1" "$2; exit}' < <(df --output=iavail,avail "${dump_storage_folder}"))
    if [ -z "${free_space}" ] ; then
        log "ERROR" "Can't get free disk space for \"${dump_storage_folder}\"" >&2
        stop_dump_loop=1
    else
        # we use bc because of possille huge numbers
        if [ -n "$("${bc_cmd}" <<<"if(${free_inodes}>${min_inodes}){print \"OK\n\"}")" ] ; then #"
          # take a size of folders by prefix mask
          folder_list=$(find "${dump_storage_folder}" -mindepth 1 -maxdepth 1 -type d -regex '^'"${dump_storage_folder}"'/'"${dump_folder_prefix}"'.+$' -exec du -sb {} \;)
          # take a bigest folder size if exist and minimal if less or not defined
          bigest_folder=$(sort -nrk1,1 <<<"${folder_list}" | awk -v min_free_space="${min_free_space}" '$1~/^[0-9]+$/{a=$1; exit}END{if((!a)||(a<min_free_space)){a=min_free_space}; print a}') #'
          take_next_dump=$("${bc_cmd}" <<<"if(${bigest_folder}>${free_space}){print \"yes\n\"}") #"
        fi
    fi
    if [ -n "${take_next_dump}" ] ; then
        # Calculate end date for this iteration (value set by reference)
        interval_end_date=$("${date_cmd}" -d "${start_date} ${increment}" "+${interval_format}")

        dump_data "${start_date}" "${metrics_prefix}" "${interval_end_date}" "${opentsdb_endpoint}" "${concurrent_jobs}" "${query_frame_size}"

        # Next iteration will start from current interval_end_date
        start_date="${interval_end_date}"
    else
        log "INFO" "Dump suspended. Waiting for a space or inodes cleanup..."
        log "INFO" "Free space \"${free_space}\", free inodes \"${free_inodes}\" for \"${dump_storage_folder}\""
        sleep "${delay_between_checks_new_folder}"
    fi

    # printout tail of the background log
    if [ -n "${show_background_log}" ] && [ -e "${background_log}" ] && [ "$(stat -c%s "${background_log}")" -gt "0" ] ; then
        log "INFO" "tail from the log \"${_c_yellow_}${background_log}${_c_none_}\" of the background process \"$(jobs -p)\""
        tail -n 5 "${background_log}" | sed 's/^./[LOG] &/g'
    fi
done


log "INFO" "Dump loop finished."
log "INFO" "Sending SIGUSR1 to background process \"${background_process_pid}\""
# set variable for Ctrl+C handler
dump_status="finished"
# send to background process info about finished dumps. it should stop when it fill not find folders to process
if ! result=$(kill -USR1 "${background_process_pid}" 2>&1) ; then
    log "WARNING" "kill returned error: \"${result}\"" >&2
fi


log "INFO" "Waiting for a background process \"${background_process_pid}\" to finish..."
while true ; do
    wait -n "${background_process_pid}" >/dev/null 2>&1
    exitcode="${?}"
    # exit code 127 - means process not found
    [ "${exitcode}" -eq "127" ] && break
done

if [ -e "${marker_for_execution}" ] ; then
    log "INFO" "Remove marker file \"${marker_for_execution}\" to inform background process about all dumps are finished"
    rm "${marker_for_execution}"
else
    log "WARNING" "Strange. The marker file \"${marker_for_execution}\" wasn't found at this point. Somebody removed it." >&2
fi

log "INFO" "Finished."
