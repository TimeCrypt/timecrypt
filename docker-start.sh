#!/bin/bash

set -euo pipefail

# How long to wait for cassandra on server startup
CASSANDRA_TIMEOUT=240
TIMECRYPT_TIMEOUT=240

export TIMECRYPT_CASSANDRA_HOST=${TIMECRYPT_CASSANDRA_HOST:=cassandra}
export TIMECRYPT_CASSANDRA_PORT=${TIMECRYPT_CASSANDRA_PORT:=9042}

export TIMECRYPT_HOST=${TIMECRYPT_HOST:=timecrypt-server}
export TIMECRYPT_PORT=${TIMECRYPT_PORT:=15000}

export TIMECRYPT_KEYSTORE_PASSWORD=${TIMECRYPT_KEYSTORE_PASSWORD:=asdfghjklasdfghjkl}

ACTION="${1:-}"
case "$ACTION" in
  server)
    # Wait for Cassandra to be available
    echo "Starting server - waiting for cassandra"
    wait-for-it -t $CASSANDRA_TIMEOUT $TIMECRYPT_CASSANDRA_HOST:$TIMECRYPT_CASSANDRA_PORT
    echo "Cassandra up"

    java -jar $SERVER_JAR_NAME
    ;;
  bash)
    /bin/bash
    ;;
  producer)
    echo "Starting producer - waiting for server"
    wait-for-it -t $TIMECRYPT_TIMEOUT $TIMECRYPT_HOST:$TIMECRYPT_PORT
    echo "Server up"

    java -jar $TESTBED_JAR_NAME --verbose "${@:2}"
    ;;
  example1)
    echo "Starting example 1 - waiting for server"
    wait-for-it -t $TIMECRYPT_TIMEOUT $TIMECRYPT_HOST:$TIMECRYPT_PORT
    echo "Server up"

    java -jar $EXAMPLE1_JAR_NAME
    ;;
  client)
    echo "Starting client - waiting for server"
    wait-for-it -t $TIMECRYPT_TIMEOUT $TIMECRYPT_HOST:$TIMECRYPT_PORT
    echo "Server up"

    java -jar $CLIENT_JAR_NAME "${@:2}"
    ;;
  *)
    echo "Action '$ACTION' undefined."
    echo ""
    echo "Usage: $0 <server|client|bash>"
    echo ""
    echo "server   start the TimeCrypt server"
    echo "client   start a timecrypt client"
    echo "producer start a producer (timecrypt testbed)"
    echo "bash     starts a bash session"
    echo ""

    exit 1
esac

exit 0
