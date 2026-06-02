---
title: OS Upgrade Considerations for PXF
description: Upgrading the operating system on PXF hosts.
sidebar_position: 3
---

PXF is compatible with these operating system platforms:

| OS Version | PXF Version              |
|------------|--------------------------|
| RHEL 8.x   | 6.3+ for Apache Cloudberry 6.20+ |
| RHEL 8.x   | 6.8+ for Apache Cloudberry 7.x   |
| RHEL 9.x   | 6.9+ for Apache Cloudberry 6.26+ |

If you plan to upgrade the operating system in your Apache Cloudberry cluster hosts to a new OS and you are running PXF in your Apache Cloudberry installation, please refer to the above table to determine which version of PXF is required.
You must perform some PXF-specific actions before and after you upgrade the OS.

The following procedures assume that you are upgrading the OS on a different set of hosts than that of the current/running Apache Cloudberry cluster.

## Pre-OS Upgrade Actions

Perform the following steps before you upgrade the operating system:

1. Upgrade PXF in your current cluster to at least the version of PXF listed in the table above and verify PXF operation **before** you commence the OS upgrade. For example, if you want to upgrade your Apache Cloudberry 6 cluster to RHEL 8, upgrade to PXF v6.3+.

1. Retain the following PXF user configuration directories, typically located in `/usr/local/pxf-gp6`: `conf/`, `keytabs/`, `lib/`, and `servers/`. If you relocated `$PXF_BASE`, retain the configuration in that directory.


## Post-OS Upgrade Actions

After you upgrade the operating system and install, configure, and verify Apache Cloudberry on the new set of hosts, perform the following procedure:

1. Download a PXF package for the upgraded OS from [VMware Tanzu Network](https://network.tanzu.vmware.com/products/vmware-greenplum/). *You must download the same version of PXF as the version that was running on the original Apache Cloudberry cluster.*

1. Install PXF for the upgraded OS on all Apache Cloudberry hosts.

1. Copy the PXF configuration files from the original cluster to `/user/local/pxf-gp6` on the upgraded OS Apache Cloudberry coordinator host. If you choose to [relocate $PXF_BASE](../administering/about_pxf_dir.md#relocating-pxf-base), copy the configuration to that directory instead.

1. Synchronize the PXF configuration to all hosts in the Apache Cloudberry cluster:

    ``` shell
    gpadmin@coordinator$ pxf cluster sync
    ```

1. Start PXF on each Apache Cloudberry host:

    ``` shell
    gpadmin@coordinator$ pxf cluster start
    ```

1. Verify that PXF can access each external data source by querying external tables that specify each PXF server configuration.

