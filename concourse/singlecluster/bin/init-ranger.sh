#!/usr/bin/env bash

# Load settings
. $GPHD_ROOT/bin/gphd-env.sh

if [ "Darwin" == $(uname -s) ]; then
   echo "Ranger script is not supported on OSX"
   exit 1
fi

# Install Mysql
yum install -y mysql-server mysql-connector-java*
service mysqld start
mysql -uroot -e "grant all privileges on *.* to 'root'@'localhost' with grant option; flush privileges;"

# Initialize Ranger
cd ../ranger && ./setup.sh
cd ../usersync && mkdir -p /var/log/ranger/usersync && ./setup.sh
service mysqld stop
