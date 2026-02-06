# Platform Extension Framework (PXF) for Apache Cloudberry (Incubating)

[![Website](https://img.shields.io/badge/Website-eebc46)](https://cloudberry.apache.org)
[![Documentation](https://img.shields.io/badge/Documentation-acd94a)](https://cloudberry.apache.org/docs)
[![Slack](https://img.shields.io/badge/Join_Slack-6a32c9)](https://inviter.co/apache-cloudberry)
[![Twitter Follow](https://img.shields.io/twitter/follow/ASFCloudberry)](https://twitter.com/ASFCloudberry)
[![WeChat](https://img.shields.io/badge/WeChat-eebc46)](https://cloudberry.apache.org/community/wechat)
[![Youtube](https://img.shields.io/badge/Youtube-gebc46)](https://youtube.com/@ApacheCloudberry)
[![GitHub Discussions](https://img.shields.io/github/discussions/apache/cloudberry)](https://github.com/apache/cloudberry/discussions)

---

## Introduction

PXF is an extensible framework that allows a distributed database like Greenplum and Apache Cloudberry to query external data files, whose metadata is not managed by the database.
PXF includes built-in connectors for accessing data that exists inside HDFS files, Hive tables, HBase tables, JDBC-accessible databases and more.
Users can also create their own connectors to other data storage or processing engines.

This project is derived from [greenplum/pxf](https://github.com/greenplum-db/pxf-archive) and customized for Apache Cloudberry.

## Repository Contents

* `external-table/` : Contains the Cloudberry extension implementing an External Table protocol handler
* `fdw/` : Contains the Cloudberry extension implementing a Foreign Data Wrapper (FDW) for PXF
* `server/` : Contains the server side code of PXF along with the PXF Service and all the Plugins
* `cli/` : Contains command line interface code for PXF
* `automation/` : Contains the automation and integration tests for PXF against the various datasources
* `ci/` : Contains CI/CD environment and scripts (including singlecluster Hadoop environment)
* `regression/` : Contains the end-to-end (integration) tests for PXF against the various datasources, utilizing the PostgreSQL testing framework `pg_regress`

## PXF Development

Below are the steps to build and install PXF along with its dependencies including Cloudberry and Hadoop.

```bash
git clone https://github.com/apache/cloudberry-pxf.git
```

### Install Dependencies

To build PXF, you must have:

1. GCC compiler, `make` system, `unzip` package, `maven` for running integration tests
2. Installed Cloudberry

    Either download and install Cloudberry RPM or build Cloudberry from the source by following instructions in the [Cloudberry](https://github.com/apache/cloudberry).

    Assuming you have installed Cloudberry into `/usr/local/cloudberry-db` directory, run its environment script:
    ```
    source /usr/local/cloudberry-db/greenplum_path.sh # For Cloudberry 2.0
    source /usr/local/cloudberry-db/cloudberry-env.sh # For Cloudberry 2.1+
    ```

3. JDK 1.8 or JDK 11 to compile/run

    Export your `JAVA_HOME`:
    ```
    export JAVA_HOME=/usr/lib/jvm/java-11-openjdk
    ```

4. Go (1.9 or later)

    You can download and install Go via [Go downloads page](https://golang.org/dl/).

    Make sure to export your `GOPATH` and add go to your `PATH`. For example:
    ```shell
    export GOPATH=$HOME/go
    export PATH=$PATH:/usr/local/go/bin:$GOPATH/bin
    ```

    Once you have installed Go, you will need the `ginkgo` tool which runs Go tests,
    respectively. Assuming `go` is on your `PATH`, you can run:
    ```
    go install github.com/onsi/ginkgo/ginkgo@latest
    ```

### Build PXF

PXF uses Makefiles to build its components. PXF server component uses Gradle that is wrapped into the Makefile for convenience.

```bash
cd ~/pxf

# Compile PXF
make
```

### Install PXF

To install PXF, first make sure that the user has sufficient permissions in the `$GPHOME` and `$PXF_HOME` directories to perform the installation. It's recommended to change ownership to match the installing user. For example, when installing PXF as user `gpadmin` under `/usr/local/cloudberry-db`:

```bash
mkdir -p /usr/local/pxf
export PXF_HOME=/usr/local/pxf
export PXF_BASE=${HOME}/pxf-base
chown -R gpadmin:gpadmin "${GPHOME}" "${PXF_HOME}"
make -C ~/pxf install
```

NOTE: if `PXF_BASE` is not set, it will default to `PXF_HOME`, and server configurations, libraries or other configurations, might get deleted after a PXF re-install.

### Run PXF

Ensure that PXF is in your path. This command can be added to your `.bashrc`:

```bash
export PATH=/usr/local/pxf/bin:$PATH
```

Then you can prepare and start up PXF by doing the following.

```bash
pxf prepare
pxf start
```

If `${HOME}/pxf-base` does not exist, `pxf prepare` will create the directory for you. This command should only need to be run once.

### Re-installing PXF after making changes

Note: Local development with PXF requires a running Cloudberry cluster.

Once the desired changes have been made, there are 2 options to re-install PXF:

1. Run `make -sj4 install` to re-install and run tests
2. Run `make -sj4 install-server` to only re-install the PXF server without running unit tests.

After PXF has been re-installed, you can restart the PXF instance using:
```bash
pxf restart
```

## Development With Docker

> [!Note]
> Since the docker container will house all Single cluster Hadoop, Cloudberry and PXF, we recommend that you have at least 4 cpus and 6GB memory allocated to Docker. These settings are available under docker preferences.

We provide a Docker-based development environment that includes Cloudberry, Hadoop, and PXF. See [automation/README.Docker.md](automation/README.Docker.md) for detailed instructions.

## IDE Setup (IntelliJ)

- Start IntelliJ. Click "Open" and select the directory to which you cloned the `pxf` repo.
- Select `File > Project Structure`.
- Make sure you have a JDK (version 1.8) selected.
- In the `Project Settings > Modules` section, select `Import Module`, pick the `pxf/server` directory and import as a Gradle module. You may see an error saying that there's
no JDK set for Gradle. Just cancel and retry. It goes away the second time.
- Import a second module, giving the `pxf/automation` directory, select "Import module from external model", pick `Maven` then click Finish.
- Restart IntelliJ
- Check that it worked by running a unit test (cannot currently run automation tests from IntelliJ) and making sure that imports, variables, and auto-completion function in the two modules.
- Optionally you can replace `${PXF_TMP_DIR}` with `${GPHOME}/pxf/tmp` in `automation/pom.xml`
- Select `Tools > Create Command-line Launcher...` to enable starting Intellij with the `idea` command, e.g. `cd ~/workspace/pxf && idea .`.

### Debugging the locally running instance of PXF server using IntelliJ

- In IntelliJ, click `Edit Configuration` and add a new one of type `Remote`
- Change the name to `PXF Service Boot`
- Change the port number to `2020`
- Save the configuration
- Restart PXF in DEBUG Mode `PXF_DEBUG=true pxf restart`
- Debug the new configuration in IntelliJ
- Run a query in CloudberryDB that uses PXF to debug with IntelliJ


## Contribute

See the [CONTRIBUTING](./CONTRIBUTING.md) file for how to make contributions dedicated to the PXF for Cloudberry Database.

## License

Under Apache License V2.0, See the [LICENSE](./LICENSE) for details.
