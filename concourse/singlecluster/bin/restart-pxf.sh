#!/usr/bin/env bash

# Load settings
root=`cd \`dirname $0\`/..;pwd`
bin=${root}/bin
. ${bin}/gphd-env.sh

${bin}/stop-pxf.sh
sleep 2s
${bin}/start-pxf.sh
