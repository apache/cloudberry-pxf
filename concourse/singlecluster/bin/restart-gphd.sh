#!/usr/bin/env bash

# Load settings
root=`cd \`dirname $0\`/..;pwd`
bin=${root}/bin
. ${bin}/gphd-env.sh

${bin}/stop-gphd.sh
sleep 8s
${bin}/start-gphd.sh
