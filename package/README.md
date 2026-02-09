PXF Packaging
============

Apache Cloudberry PXF (Platform Extension Framework) consists of 3 groups of artifacts, each developed using a different underlying technology:

* Apache Cloudberry extension -- written in C; when built, produces a `pxf.so` library and configuration files
* PXF Server -- written in Java; when built, produces a `pxf.war` file, Tomcat server, dependent JAR files, templates and scripts
* Script Cluster Plugin -- written in Go; when built, produces a `pxf-cli` executable

The PXF build system can create an RPM package on CentOS platform and a DEB package on Ubuntu platform,
respectively. PXF compiles against and generates packages for Apache Cloudberry.

For example, `cloudberry-pxf-1.2.3-1.el7.x86_64.rpm` represents an RPM package of PXF version 1.2.3 intended to work with
Apache Cloudberry on CentOS / Red Hat 7 operating systems.

## PXF RPM specification
On CentOS platforms PXF product is packaged as an RPM. The specification on how to build the RPM is provided by the
`cloudberry-pxf.spec` file in this directory. The following key design decisions were made:

* the name of the RPM package is `cloudberry-pxf`
* to install a newer RPM package, a user will have to upgrade the PXF RPM
* the RPM installs PXF server into `/usr/local/cloudberry-pxf-[VERSION]` directory (e.g. `/usr/local/cloudberry-pxf-1.2.3`)
* the RPM is relocatable, a user can specify --prefix option when installing the RPM to install the server into another directory
* the PXF Apache Cloudberry extension is initially installed by RPM alongside the PXF server and is not initially active
* the PXF Apache Cloudberry extension is copied into Cloudberry install location during `pxf init` command issued by a user after the install
* the PXF RPM version number follows 3-number semantic versioning and must be provided during the RPM build process
* the PXF RPM release number is usually specified as `1`
* example PXF RPM names are : `cloudberry-pxf-1.2.3-1.el7.x86_64.rpm` and `cloudberry-pxf-1.2.3-1.el8.x86_64.rpm` 

## PXF RPM build process

To build an RPM, follow these steps:
1. Install the `rpm-build` package: `sudo yum install rpm-build`
2. Install Apache Cloudberry
3. Run `source $GPHOME/greenplum_path.sh`(for Cloudberry 2.0) or `source $GPHOME/cloudberry-env.sh` (for Cloudberry 2.1+) to configure your `PATH` to be able to find `pg_config` program
4. Run `make clean rpm` from the top-level directory to build artifacts and assemble the RPM
5. The RPM will be available in `build/rpmbuild/RPMS` directory


## PXF RPM installation process
To install PXF from an RPM, follow these steps:
1. Build or download PXF RPM for Apache Cloudberry. The following example will assume
   that PXF version `1.2.3` will be installed to work with Apache Cloudberry.
2. Decide which OS user will own the PXF installation. If PXF is installed alongside Apache Cloudberry, the user that owns the PXF
installation should either be the same as the one owning the Cloudberry installation or have write privileges to the
Cloudberry installation directory. This is necessary to be able to register the PXF Apache Cloudberry extension with Cloudberry.
3. If a previous PXF version has been installed, stop the PXF server.
4. As a superuser, run `rpm -Uvh cloudberry-pxf-1.2.3-1.el7.x86_64.rpm` to install the RPM into `/usr/local/cloudberry-pxf-1.2.3`
5. As a superuser, run `chown gpadmin:gpadmin /usr/local/cloudberry-pxf-1.2.3` to change ownership of PXF installation to the user `gpadmin`.
Specify a different user other than `gpadmin`, if desired.

After these steps, the PXF product will be installed and is ready to be configured. If there was a previous installation of
PXF, the files and the runtime directories from the older version will be removed.
The PXF configuration directory should remain intact. You will need to have Java installed to run the PXF server.

## PXF removal process
To remove the installed PXF package, follow these steps:
1. Stop the PXF server.
2. As a superuser, run `rpm -e cloudberry-pxf`. This will remove all files installed by the RPM package
and the PXF runtime directories. The PXF configuration directory should remain intact.
