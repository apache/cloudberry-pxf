# load singlecluster environment
. $GPHD_ROOT/bin/gphd-env.sh

export HIVE_OPTS="-hiveconf derby.stream.error.file=$LOGS_ROOT/derby.log -hiveconf javax.jdo.option.ConnectionURL=jdbc:derby:;databaseName=$HIVE_STORAGE_ROOT/metastore_db;create=true"
export HIVE_SERVER_OPTS="-hiveconf derby.stream.error.file=$LOGS_ROOT/derby.log -hiveconf ;databaseName=$HIVE_STORAGE_ROOT/metastore_db;create=true"
export HADOOP_HOME=$HADOOP_ROOT
export HADOOP_CLASSPATH="$TEZ_CONF:$TEZ_JARS:$HADOOP_CLASSPATH"
