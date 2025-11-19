#!/usr/bin/env bash

# Load settings
root=`cd \`dirname $0\`/..;pwd`
bin=${root}/bin
. ${bin}/gphd-env.sh

pushd ${ZOOKEEPER_ROOT} > /dev/null
bin/zkServer.sh stop
popd > /dev/null
