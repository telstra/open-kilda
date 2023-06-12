#!/bin/bash

# ./otsdb-to-vm.sh opentsdb.example.com:4242 victoria-metrics.example.com:4242 2022-01-01 2022-01-31 kilda. day
# This command will migrate data from OpenTSDB running at "opentsdb.example.com:4242"
#to Victoria Metrics running at "victoria-metrics.example.com:4242" for the time period
#between January 1, 2022 and January 31, 2022, using the metrics prefix "my-metrics-prefix"
#and a time interval of one day. You can customize the command by replacing the parameters
#with your own values.

# Check for required parameters
if [[ $# -lt 4 ]]; then
  echo "Usage: $0 OPENTSDB_ENDPOINT VICTORIA_ENDPOINT TIME_START TIME_STOP [hour|day] CONCURRENT_JOBS QUERY_FRAME_SIZE

  This tool dumps the data from an OpenTSDB and restore it to a VictoriaMetrics service.

  OPENTSDB_ENDPOINT openTSDB endpoint

  VICTORIA_ENDPOINT VictoriaMetrics endpoint

  TIME_START time since the data is dumped

  DATE_STOP time where to stop dumping

  [hour|day] time frame size to dump data. Default is day.

  CONCURRENT_JOBS number of concurrent jobs to run. Default is 1.

  QUERY_FRAME_SIZE number of data points to query from OpenTSDB at once. Default is 180.

  Examples:

  ./otsdb-to-vm.sh opentsdb.example.com:4242 victoria-metrics.example.com:4242 2022-01-01 2022-01-31 kilda. day
  ./otsdb-to-vm.sh opentsdb.example.com:4242 victoria-metrics.example.com:4242 2022-01-01T00:00:00 2022-01-01T23:59:59 kilda. hour"
  exit 1
fi


# Set parameters
opentsdb_endpoint="$1"
victoria_metrics_endpoint="$2"
start_date="$3"
end_date="$4"
metrics_prefix="$5"
interval="${6:-day}"
concurrent_jobs="${7:-1}"
query_frame_size="${8:-180}"

# Set time interval
case $interval in
  hour)
    interval_format="%Y-%m-%dT%H:00:00"
    increment="1 hour"
    ;;
  day)
    interval_format="%Y-%m-%d"
    increment="1 days"
    ;;
  *)
    echo "Invalid interval: $interval"
    exit 1
    ;;
esac


if [[ "$(docker images -q kilda-otsdb-dump-restore 2> /dev/null)" == "" ]]; then
  echo "Docker image kilda-otsdb-dump-restore not found. Please build it first." >&2
  exit 1
fi

# Define function to dump data from OpenTSDB
function dump_data {
    docker run --rm --network="host" -v "opentsdb-data-${5}":/tmp kilda-otsdb-dump-restore kilda-otsdb-dump --query-frame-size "${7}" --concurrent "${6}" --metrics-prefix "${2}" --time-stop "${3}" "${4}" "${1}"
}

# Define function to restore data to Victoria Metrics
function restore_data {
    docker run --rm --network="host" -v "opentsdb-data-${2}":/tmp kilda-otsdb-dump-restore kilda-otsdb-restore "${1}" && docker volume rm "opentsdb-data-${2}" || echo "Failed to restore data to Victoria Metrics" >&2
}

function increment_date()
{
    local  __resultvar=$1
    eval $__resultvar=$(date -d "${start_date} ${increment}" +${interval_format})
}


# Loop through dates
while [[ "$start_date" < "$end_date" ]]; do
    # trim : from date
    volume_subfix=$(echo "${start_date}" | tr -d :)
    # Create Docker volume for this iteration
    docker volume create "opentsdb-data-${volume_subfix}"

    # Calculate end date for this iteration
    increment_date interval_end_date

    echo "Dumping data from ${start_date} to ${interval_end_date}"
    dump_data "${start_date}" "${metrics_prefix}" "${interval_end_date}" "${opentsdb_endpoint}" "${volume_subfix}" "${concurrent_jobs}" "${query_frame_size}"

    echo "Restoring data from ${start_date} to ${interval_end_date} in background"
    # restore_data "${victoria_metrics_endpoint}" "${volume_subfix}" &

    # Increment date by time interval
    increment_date start_date
done

wait
