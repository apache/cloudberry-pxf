#!/usr/bin/env bash

# Load settings
root=`cd \`dirname $0\`/..;pwd`
bin=${root}/bin
. ${bin}/gphd-env.sh

# TODO cleanup after hbase?

echo Stopping HBase standalone...
${HBASE_ROOT}/bin/stop-hbase.sh