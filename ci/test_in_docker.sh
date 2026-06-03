#!/usr/bin/env bash
# This script depends on ggdb6_pxf_regress
set -exo pipefail

export PXF_SRC=${PXF_SRC:-/tmp/build/pxf_src}
export DEV_HOME=${DEV_HOME:-/home/gpadmin}
export GPHOME=${GPHOME:-/usr/local/greengage-db-devel}
export PXF_HOME=${PXF_HOME:-"$GPHOME/pxf"}

# Set hostname to make certificate valid and PXF server accessible
hostname mdw
echo "127.0.0.1    mdw" >> /etc/hosts

# unpack gpdb and pxf; run gpdb cluster and pxf server
$PXF_SRC/concourse/scripts/test_pxf.bash

# Enable basePath in the PXF server to allow writable tables test
sed -i \
  -e 's|</configuration>|<property><name>pxf.fs.basePath</name><value>/tmp/pxf/</value></property></configuration>|g' \
  $PXF_HOME/servers/default/pxf-site.xml

# tweak necessary folders to run regression tests later
chown gpadmin:gpadmin -R $PXF_SRC

# Display the diff if we fail
trap "find $PXF_SRC -type f -name '*.diffs' -exec cat {} \;" ERR

# test fdw and external-table
echo "=============================="
echo "Run tests with PXF (HTTP)"
echo "=============================="
su - gpadmin -c "
    source '$GPHOME/greengage_path.sh';
    source '$DEV_HOME/gpdb_src/gpAux/gpdemo/gpdemo-env.sh';
    cd $PXF_SRC/fdw &&
    make install &&
    make installcheck-http &&
    cd ../external-table/ &&
    make install &&
    make installcheck;
"

# Switch to PXF SSL
if [[ "$PXF_PROTOCOL" = "https" ]]; then
    # Pass through PXF environment variables to gpadmin user
    echo "--------------------------------------"
    echo "Init SSL env variables for PXF service"
    echo "--------------------------------------"
    env | grep -E 'PXF_SSL|PXF_HOST|PXF_PROTOCOL' | sed 's/^/export /' >> $DEV_HOME/.bash_profile
    env | grep -E 'PXF_SSL|PXF_HOST|PXF_PROTOCOL' | sed 's/^/export /' >> $DEV_HOME/.bashrc

    # Restart PXF
    /usr/sbin/sshd
    su - gpadmin -c "
       source '$GPHOME/greengage_path.sh';
       source '$DEV_HOME/gpdb_src/gpAux/gpdemo/gpdemo-env.sh';
       $GPHOME/bin/gpstop -arM fast && $PXF_HOME/bin/pxf restart
    "

    # test fdw and external-table with PXF SSL
    echo "=============================="
    echo "Run tests with PXF SSL (HTTPS)"
    echo "=============================="
    su - gpadmin -c "
        source '$GPHOME/greengage_path.sh';
        source '$DEV_HOME/gpdb_src/gpAux/gpdemo/gpdemo-env.sh';
        cd $PXF_SRC/fdw &&
        make install &&
        make installcheck-https &&
        cd $PXF_SRC/external-table/ &&
        make install &&
        make installcheck;
    "
fi
