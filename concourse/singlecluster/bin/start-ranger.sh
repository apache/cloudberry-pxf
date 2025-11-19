#!/usr/bin/env bash

# Load settings
root=`cd \`dirname $0\`/..;pwd`
bin=${root}/bin
. ${bin}/gphd-env.sh

if [ "Darwin" == $(uname -s) ]; then
   echo "Ranger script is not supported on OSX"
   exit 1
fi

# Start mysql
service mysqld start

# Start usersync
ranger-usersync start

# Open browser with http://localhost:6080 and log on using admin/admin
ranger-admin start
