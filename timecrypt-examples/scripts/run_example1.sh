#!/bin/bash

LOCAL_PATH=$(cd -P -- "$(dirname -- "$0")" && pwd -P)
CUR_PATH=$(pwd)
cd ${LOCAL_PATH}/../

IP="127.0.0.1"

java -jar target/timecrypt-example-usage-jar-with-dependencies.jar $IP

cd ${CUR_PATH}