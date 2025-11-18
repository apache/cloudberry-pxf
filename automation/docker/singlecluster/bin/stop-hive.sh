#!/usr/bin/env bash

# Load settings
root=`cd \`dirname $0\`/..;pwd`
bin=${root}/bin
. ${bin}/gphd-env.sh

${bin}/hive-service.sh hiveserver2 stop
${bin}/hive-service.sh metastore stop
