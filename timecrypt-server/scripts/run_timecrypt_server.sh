#!/bin/bash

LOCAL_PATH=$(cd -P -- "$(dirname -- "$0")" && pwd -P)
CUR_PATH=$(pwd)
cd ${LOCAL_PATH}/../

memusage=24576m
port=15000
aThreads=2
cThreads=16
wThreads=32
treeCache=2000
blockCache=20000000
kfactor=64
ips="127.0.0.1"

if ! [ -z "$1" ]
  then
    kfactor=$1

fi

echo "Running Server with kFactor ${kfactor}"

java -Xmx${memusage} -cp target/timecrypt-1.0-full.jar ch.ethz.dsg.timecrypt.Serverrt} ${aThreads} ${cThreads} ${wThreads} ${treeCache} ${blockCache} ${kfactor} ${ips}

cd ${CUR_PATH}