#!/bin/bash

LOCAL_PATH=$(cd -P -- "$(dirname -- "$0")" && pwd -P)
CUR_PATH=$(pwd)
cd ${LOCAL_PATH}/../

memusage=24576m
export TIMECRYPT_PORT=15000
export TIMECRYPT_SERVER_GROUP_THREADS=2;
export TIMECRYPT_WORKER_GROUP_THREADS=16;
export TIMECRYPT_EVENT_EXECUTOR_THREADS=32;
export TIMECRYPT_TREE_CACHE=2000;
export TIMECRYPT_BLOCK_CACHE=20000000;
export TIMECRYPT_K_FACTOR=64;
export TIMECRYPT_IN_MEMORY="true"

echo "Starting TimeCrypt with in-memory Server"

java -Xmx${memusage} -cp target/timecrypt-server-jar-with-dependencies.jar ch.ethz.dsg.timecrypt.Server

cd ${CUR_PATH}