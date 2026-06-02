---
title: PXF Post-gpupgrade Actions
description: Post-upgrade tasks for PXF after an Apache Cloudberry version upgrade.
sidebar_position: 6
---

If you are running PXF with Apache Cloudberry 5.x and plan to use `gpupgrade` for an in-place upgrade to Apache Cloudberry 6.x, you must perform these steps after running `gpupgrade`.


## Post-gpupgrade Actions

Perform the following steps after running `gpupgrade`:

1. Determine if the `gpupgrade` process succeeded or failed.

1. **If the `gpupgrade` process failed**:

    1. Run the following commands to roll back your PXF installation to its previous state (before you ran `pxf-pre-gpupgrade`):

        ``` shell
        gpadmin@coordinator$ export GPHOME=<greenplum5-install-dir>
        gpadmin@coordinator$ /usr/local/pxf-gp5/bin/pxf-post-gpupgrade
        ```

        **Note:** The `pxf-post-upgrade` script must connect to your running Apache Cloudberry 5.x cluster. By default, it attempts to connect to the `gpadmin` database on `localhost` on port `5432` as the `gpadmin` user, no password. If you need to customize these settings, refer to [Customizing the Apache Cloudberry Connection Parameters](#customizing-the-greenplum-connection-parameters) for instructions on setting environment variables for this purpose.

    1. Restart the PXF that was running in your Apache Cloudberry 5.x installation.

        ``` shell
        gpadmin@coordinator$ /usr/local/pxf-gp5/bin/pxf cluster start
        ```

    1. You may choose to uninstall the PXF for Apache Cloudberry 6 package.

    1. **Exit this procedure.**

1. **If the `gpupgrade` process succeeded, perform the remaining steps in this procedure.**

1. Configure PXF as though it was a fresh install in Apache Cloudberry 6.x:

    ``` shell
    gpadmin@coordinator$ export GPHOME=<greenplum6-install-dir>
    gpadmin@coordinator$ /usr/local/pxf-gp6/bin/pxf-post-gpupgrade
    ```

    **Note:** The `pxf-post-upgrade` script must connect to your running Apache Cloudberry 6.x cluster. By default, it attempts to connect to the `gpadmin` database on `localhost` on port `5432` as the `gpadmin` user, no password. If you need to customize these settings, refer to [Customizing the Apache Cloudberry Connection Parameters](#customizing-the-greenplum-connection-parameters) for instructions on setting environment variables for this purpose.

1. If you nave not relocated your `$PXF_BASE`, you must copy the PXF configuration from the PXF Apache Cloudberry 5.x install location to the PXF Apache Cloudberry 6.x install location. For example:

    ``` bash
    gpadmin@coordinator$ for dir in conf lib servers keytabs; do
        cp -aiv /usr/local/pxf-gp5/$dir/. /usr/local/pxf-gp6/$dir/
    done
    ```

1. Synchronize the PXF configuration from the Apache Cloudberry coordinator host to the standby and segment hosts:

    ``` shell
    gpadmin@coordinator$ /usr/local/pxf-gp6/bin/pxf cluster sync
    ```

1. Start the PXF installed for Apache Cloudberry 6.x.

    ``` shell
    gpadmin@coordinator$ /usr/local/pxf-gp6/bin/pxf cluster start
    ```

1. Update the `$PATH` in `.bashrc` or `.bash_profile` shell initialization scripts, replacing any occurrences of `/usr/local/pxf-gp5` with `/usr/local/pxf-gp6`.

1. Verify that PXF can access each external data source by querying external tables that specify each PXF server configuration.

1. (_Optional_) Uninstall the PXF for Apache Cloudberry 5 package on every segment host in the GPDB cluster; this operation requires `sudo` privileges. For example:

    ``` shell
    $ yum remove pxf-gp5
    ```

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

