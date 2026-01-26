#!/usr/bin/env bash

# Load settings
root=`cd \`dirname $0\`/..;pwd`
bin=${root}/bin
. ${bin}/gphd-env.sh

if [ "$START_HBASE" == "true" ]; then
	echo Stopping HBase...
	${bin}/stop-hbase.sh

	echo Stopping Zookeeper...
	${bin}/stop-zookeeper.sh
fi

if [ "$START_PXF" == "true" ]; then
	echo Stopping PXF...
	${bin}/stop-pxf.sh
fi

if [ "$START_YARN" == "true" ]; then
	echo Stopping YARN...
	${bin}/stop-yarn.sh
fi

if [ "$START_HIVEMETASTORE" == "true" ]; then
	${bin}/stop-hive.sh
fi

echo Stopping HiveServer...
${bin}/hive-service.sh hiveserver stop
echo Stopping HiveServer2...
${bin}/hive-service.sh hiveserver2 stop

echo Stopping HDFS...
${bin}/stop-hdfs.sh
