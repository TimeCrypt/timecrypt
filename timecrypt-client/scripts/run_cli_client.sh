#!/bin/bash

LOCAL_PATH=$(cd -P -- "$(dirname -- "$0")" && pwd -P)
CUR_PATH=$(pwd)
cd ${LOCAL_PATH}/../

java -Xmx${memusage} -cp target/timecrypt-client-jar-with-dependencies.jar

cd ${CUR_PATH}