#!/usr/bin/env bash

# Load settings
root=`cd \`dirname $0\`/..;pwd`
bin=${root}/bin
. ${bin}/gphd-env.sh

# Initialize PXF instances
for (( i=0; i < ${SLAVES}; i++ ))
do
	echo initializing PXF instance ${i}
	${bin}/pxf-service.sh init ${i}
	if [ $? -ne 0 ]; then
		echo
		echo tcServer instance \#${i} initialization failed
		echo check console output
		exit 1
	fi
done
