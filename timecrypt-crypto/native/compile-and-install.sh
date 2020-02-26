#!/usr/bin/env bash

LOCAL_PATH=$(cd -P -- "$(dirname -- "$0")" && pwd -P)
TARGET_DIR="$(dirname -- "$0")/../target/$1"

CUR_PATH=$(pwd)

if [[ ! -d "$LOCAL_PATH/$1" ]]; then
  echo "nothing to compile for $1"
  exit 1
fi

set -e

mkdir -p "$TARGET_DIR"

cp "$LOCAL_PATH/$1/"* "$TARGET_DIR"
cd "$TARGET_DIR"

cmake .
make

FOLDER=""

if [[ "$OSTYPE" == "linux-gnu" ]]; then
        FOLDER="linux_64"
elif [[ "$OSTYPE" == "darwin"* ]]; then
        FOLDER="osx_64"
elif [[ "$OSTYPE" == "cygwin" ]]; then
        FOLDER="windows_64"
elif [[ "$OSTYPE" == "msys" ]]; then
        FOLDER="windows_64"
elif [[ "$OSTYPE" == "freebsd"* ]]; then
        FOLDER="linux_64"
else
        echo "unknown OS - aborting"
        exit 1
fi

echo "OS detected $FOLDER"

OUTPUT_DIR="../classes/META-INF/lib/$FOLDER"

mkdir -p "$OUTPUT_DIR"
cp out/* "$OUTPUT_DIR"

cd $CUR_PATH
