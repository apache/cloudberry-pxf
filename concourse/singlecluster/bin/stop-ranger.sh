#!/usr/bin/env bash

# Load settings
root=`cd \`dirname $0\`/..;pwd`
bin=${root}/bin
. ${bin}/gphd-env.sh

if [ "Darwin" == $(uname -s) ]; then
   echo "Ranger script is not supported on OSX"
   exit 1
fi

ranger-admin stop
ranger-usersync stop
service mysqld stop
