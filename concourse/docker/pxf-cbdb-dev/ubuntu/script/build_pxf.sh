export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
export GPHOME=/usr/local/cloudberry-db
export PATH=$GPHOME/bin:$PATH
source $GPHOME/cloudberry-env.sh

sudo apt update
sudo apt install -y openjdk-11-jdk maven

cd /home/gpadmin/workspace/cloudberry-pxf

# Set Go environment
export GOPATH=$HOME/go
export PATH=$PATH:/usr/local/go/bin:$GOPATH/bin
mkdir -p $GOPATH
export PXF_HOME=/usr/local/pxf
mkdir -p $PXF_HOME

# Build all PXF components
make all

# Install PXF
make install

# Set up PXF environment

export PXF_BASE=$HOME/pxf-base
export PATH=$PXF_HOME/bin:$PATH

# Initialize PXF
pxf prepare
pxf start

# Verify PXF is running
pxf status