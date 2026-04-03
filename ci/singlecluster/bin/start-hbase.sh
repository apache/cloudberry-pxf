#!/usr/bin/env bash

# Load settings
root=`cd \`dirname $0\`/..;pwd`
bin=${root}/bin
. ${bin}/gphd-env.sh

echo Starting HBase standalone...
${HBASE_ROOT}/bin/start-hbase.sh