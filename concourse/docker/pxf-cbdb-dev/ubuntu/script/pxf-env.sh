#!/bin/bash
# PXF Environment Variables
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
export GPHOME=/usr/local/cloudberry-db
export PATH=$GPHOME/bin:$PATH
export GOPATH=$HOME/go
export PATH=$PATH:/usr/local/go/bin:$GOPATH/bin
export PXF_HOME=/usr/local/pxf
export PXF_BASE=$HOME/pxf-base
export PATH=$PXF_HOME/bin:$PATH

# Source Cloudberry environment
if [ -f "$GPHOME/cloudberry-env.sh" ]; then
    source $GPHOME/cloudberry-env.sh
fi

# Source demo cluster environment if available
if [ -f "/home/gpadmin/workspace/cloudberry/gpAux/gpdemo/gpdemo-env.sh" ]; then
    source /home/gpadmin/workspace/cloudberry/gpAux/gpdemo/gpdemo-env.sh
fi

echo "PXF environment loaded successfully"

