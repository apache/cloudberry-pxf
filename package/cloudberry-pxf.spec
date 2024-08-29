# Disable repacking of jars, since it takes forever
%define __jar_repack %{nil}

# Disable build-id in rpm
%define _build_id_links none
# Disable automatic dependency processing both for requirements and provides
AutoReqProv: no

Name:      cloudberry-pxf
Version:   %{pxf_version}
Release:   %{pxf_release}%{?dist}

Summary:   Cloudberry PXF (Platform Extension Framework) for advanced data access
License:   %{license}
URL:       https://cloudberrydb.org
Vendor:    %{vendor}
Group:     Applications/Databases

Prefix:   /usr/local/%{name}-%{version}

# Java server can be installed on a new node, only bash is needed for
# management scripts

Requires: bash

# Require Cloudberry Database - .so file makes sense only when
# installing on Cloudberry node, so inherit Cloudberry's dependencies
# implicitly

Requires: cloudberry-db

# Weak dependencies either OpenJDK 8 or 11
Suggests: java-1.8.0-openjdk
Suggests: java-11-openjdk

%description
Cloudberry PXF (Platform Extension Framework) is an advanced data
access framework that provides connectivity to a wide range of data
sources. It enables high-speed, parallel data access across
distributed systems, making it an essential component for performing
advanced analytics with the Cloudberry Database. PXF seamlessly
integrates and efficiently queries external data sources, including,
but not limited to:

- HDFS files
- Hive tables
- HBase tables
- Databases that support JDBC
- Cloud-based data stores such as Amazon S3 and Google Cloud Storage (GCS)

Supported file formats include, but are not limited to:

- Text files (e.g., CSV, TSV)
- Sequence files
- Avro files
- Parquet files
- ORC files
- RCFile (Record Columnar File)
- JSON files
- Avro Object Container Files

Whether accessing structured, semi-structured, or unstructured data,
PXF ensures that users can efficiently interact with a diverse set of
data environments and file formats. The examples provided above
represent only a subset of the broad range of sources and formats
supported by PXF.

For more information, visit the official Cloudberry Database website
at https://cloudberrydb.org.

%prep
# If the pxf_version macro is not defined, it gets interpreted as a literal string, need %% to escape it
if [ %{pxf_version} = '%%{pxf_version}' ] ; then
  echo "The macro (variable) pxf_version must be supplied as rpmbuild ... --define='pxf_version [VERSION]'"
  exit 1
fi

%install
%__mkdir -p %{buildroot}/%{prefix}
%__cp -R %{_sourcedir}/* %{buildroot}/%{prefix}

# Create symlink
%__ln_s %{prefix} %{buildroot}/usr/local/%{name}

%post
sed -i "s|directory =.*|directory = '${RPM_INSTALL_PREFIX}/gpextable/'|g" "${RPM_INSTALL_PREFIX}/gpextable/pxf.control"
sed -i "s|module_pathname =.*|module_pathname = '${RPM_INSTALL_PREFIX}/gpextable/pxf'|g" "${RPM_INSTALL_PREFIX}/gpextable/pxf.control"

# Change ownership to gpadmin.gpadmin if the gpadmin user exists
if id "gpadmin" &>/dev/null; then
    chown -R gpadmin:gpadmin ${RPM_INSTALL_PREFIX}
fi

%files
%{prefix}
/usr/local/%{name}

# If a file is not marked as a config file, or if a file has not been altered
# since installation, then it will be silently replaced by the version from the
# RPM.

# If a config file has been edited on disk, but is not actually different from
# the file in the RPM then the edited version will be silently left in place.

# When a config file has been edited and is different from the file in
# the RPM, then the behavior is the following:
# - %config(noreplace): The edited version will be left in place, and the new
#                       version will be installed with an .rpmnew suffix.
# - %config: The new file will be installed, and the the old edited version
#            will be renamed with an .rpmsave suffix.

# Configuration directories/files
%config(noreplace) %{prefix}/conf/pxf-application.properties
%config(noreplace) %{prefix}/conf/pxf-env.sh
%config(noreplace) %{prefix}/conf/pxf-log4j2.xml
%config(noreplace) %{prefix}/conf/pxf-profiles.xml

%pre
# cleanup files and directories created by 'pxf init' command
# only applies for old installations (pre 6.0.0)
%__rm -f "${RPM_INSTALL_PREFIX}/conf/pxf-private.classpath"
%__rm -rf "${RPM_INSTALL_PREFIX}/pxf-service"

%posttrans
# PXF v5 RPM installation removes the run directory during the %preun step.
# The lack of run directory prevents PXF v6+ from starting up.
# %posttrans of the new package is the only step that runs after the %preun
# of the old package
%{__install} -d -m 700 "${RPM_INSTALL_PREFIX}/run"

%preun
# Remove symlink on uninstall
if [ $1 -eq 0 ] ; then
    %__rm -f /usr/local/%{name}
fi
