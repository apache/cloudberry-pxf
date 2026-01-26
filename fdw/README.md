# PXF Foreign Data Wrapper for Cloudberry

This Cloudberry extension implements a Foreign Data Wrapper (FDW) for PXF.

PXF is a query federation engine that accesses data residing in external systems
such as Hadoop, Hive, HBase, relational databases, S3, Google Cloud Storage,
among other external systems.

### Development

## Compile

To compile the PXF foreign data wrapper, we need a Cloudberry installation and libcurl.

    export PATH=/usr/local/cloudberry-db/bin/:$PATH

    make

## Install

    make install

## Regression

    make installcheck
