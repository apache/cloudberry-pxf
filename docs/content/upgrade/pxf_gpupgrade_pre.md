---
title: PXF Pre-gpupgrade Actions
description: Preparing PXF for an Apache Cloudberry version upgrade.
sidebar_position: 5
---

If you are running PXF with Apache Cloudberry 5.x and plan to use `gpupgrade` for an in-place upgrade to Apache Cloudberry 6.x, you must perform these steps prior to running `gpupgrade`.


## Pre-gpupgrade Actions

Before you run `gpupgrade`, perform the following steps:

1. Upgrade your Apache Cloudberry 5.x PXF installation to the latest release (6.3.0 or newer); refer to the [Upgrading PXF](./upgrade_landing.md) documentation for instructions on upgrading PXF.

1. Note the PXF version.

1. Install the Apache Cloudberry 6-specific PXF package of the same version on every segment host in the cluster. You need only install the PXF package; you do not need to run the `pxf cluster prepare` or `pxf cluster register` commands.

1. Log in to the coordinator host of your Apache Cloudberry 5.x cluster:

    ``` shell
    $ ssh gpadmin@<coordinator>
    gpadmin@coordinator$ 
    ```

1. Run the following commands to set up your environment; substitute the file system path to your Apache Cloudberry 5.x install directory:

    ``` shell
    gpadmin@coordinator$ export GPHOME=<greenplum5-install-dir>
    gpadmin@coordinator$ /usr/local/pxf-gp5/bin/pxf-pre-gpupgrade
    ```

    **Note:** The `pxf-pre-gpupgrade` script must connect to your running Apache Cloudberry 5.x cluster. By default, it attempts to connect to the `gpadmin` database on `localhost` on port `5432` as the `gpadmin` user (no password). If you need to customize these settings, refer to [Customizing the Apache Cloudberry Connection Parameters](#customizing-the-greenplum-connection-parameters) for instructions on setting environment variables for this purpose.

1. Stop PXF; note that PXF external tables will be inaccessible during the Apache Cloudberry upgrade process.

    ``` shell
    gpadmin@coordinator$ /usr/local/pxf-gp5/bin/pxf cluster stop
    ```

1. PXF is ready for upgrading by `gpupgrade`; return to the `gpupgrade` documentation to upgrade from Apache Cloudberry 5.x to Apache Cloudberry 6.x.


## Customizing the Apache Cloudberry Connection Parameters

The PXF scripts that you run before and after `gpupgrade` must connect to your running Apache Cloudberry 5.x or 6.x cluster. By default, the scripts attempt to connect to the `gpadmin` database on `localhost` on port `5432` as the `gpadmin` user, no password. If you need to customize these settings, you can do so by specifying the following environment variables:

| Environment Variable | Default Value | Description                                           |
|----------------------|---------------|-------------------------------------------------------|
| `$PGHOST`            | localhost     | The host name or IP address of the Apache Cloudberry coordinator host. |
| `$PGPORT`            | 5432          | The port number to connect to on the coordinator host.     |
| `$PGDATABASE`        | gpadmin       | The name of the database. |
| `$PGUSER`            | gpadmin       | The Apache Cloudberry user name.     |
| `$PGPASSWORD`        | _none_        | The password for the user. |

Refer to the [Environment Variables](https://www.postgresql.org/docs/9.4/libpq-envars.html) topic in the PostgreSQL documentation for more details.

