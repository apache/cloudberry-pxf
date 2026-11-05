# pxf-iceberg-connector

Overview

An Apache Iceberg connector for PXF, ported from
[GreengageDB/pxf](https://github.com/GreengageDB/pxf) (`server/pxf-iceberg`).
Covers:
- Reading (Parquet, ORC, Avro formats via format-specific ReaderFactory impls)
- Writing (Parquet only, via WriterFactory)
- Supported catalog types: Hive Metastore, REST
- Predicate pushdown and column projection during fragmentation
- Iceberg branch support for reads and writes

Writes are committed per PXF host: each PXF instance appends the data files produced by its
local segments to the Iceberg table once all of them have finished writing. The Greengage
"master-commit" protocol (`ext_protocol_version 'v1'`), which commits all files in a single
snapshot from the coordinator, is not supported yet.

Known limitations:
- An `INSERT` is not atomic: each PXF host commits its own snapshot, and PXF has no way to tell a
  plugin that a write failed, so a failed or cancelled `INSERT` may leave some of its rows in the table.
- Only Parquet data files are written.
- A custom trust store (`sslTrustStorePath`) is supported for the Hive Metastore catalog only; for
  the REST catalog configure `javax.net.ssl.trustStore` in `PXF_JVM_OPTS`.

There are few ways to define Cloudberry tables that will be used for work with Iceberg. 

**Example of reading using external tables**
```sql
CREATE EXTERNAL TABLE pxf_iceberg(id int, first_name text, last_name text, age int)
LOCATION ('pxf://test.customer?PROFILE=iceberg&Server=iceberg_server')
FORMAT 'CUSTOM' (FORMATTER='pxfwritable_import');

SELECT * FROM pxf_iceberg;       
```

**Example of writing using external tables**
```sql
CREATE WRITABLE EXTERNAL TABLE pxf_iceberg (
    id int, first_name text, last_name text, age int
)
LOCATION ('pxf://test.customer?PROFILE=iceberg&Server=iceberg_server')
FORMAT 'CUSTOM' (FORMATTER='pxfwritable_export');

INSERT INTO pxf_iceberg
	select
		generate_series(1,3000000) as id,
		md5(random()::text) as first_name, 
		md5(random()::text) as last_name,
		random() as age;   
```

**Example of work using Foreign Data Wrappers through REST Catalog**
```sql
CREATE FOREIGN DATA WRAPPER iceberg_pxf_fdw
                    HANDLER pxf_fdw_handler
                    VALIDATOR pxf_fdw_validator
                    OPTIONS ( protocol 'iceberg', mpp_execute 'all segments' );
                   
CREATE SERVER iceberg_rest_server
        FOREIGN DATA WRAPPER iceberg_pxf_fdw
        OPTIONS (catalog_type 'REST', uri 'http://iceberg-rest:8181');
                   
CREATE USER MAPPING FOR CURRENT_USER SERVER iceberg_rest_server;  

CREATE FOREIGN TABLE fdw_iceberg(id int, first_name text, last_name text, age int)
        SERVER iceberg_rest_server
        OPTIONS (resource 'test.customer');

INSERT INTO fdw_iceberg
	select
		generate_series(1,3000000) as id,
		md5(random()::text) as first_name, 
		md5(random()::text) as last_name,
		random() as age;   
       
SELECT * FROM fdw_iceberg;        
        
```

**Example of work using Foreign Data Wrappers through Hive Catalog**
```sql
CREATE FOREIGN DATA WRAPPER iceberg_pxf_fdw
                    HANDLER pxf_fdw_handler
                    VALIDATOR pxf_fdw_validator
                    OPTIONS ( protocol 'iceberg', mpp_execute 'all segments' );

CREATE SERVER iceberg_hive_server
         FOREIGN DATA WRAPPER iceberg_pxf_fdw
         OPTIONS (catalog_type 'HIVE_METASTORE', uri 'thrift://hive-metastore:9083');

CREATE USER MAPPING FOR CURRENT_USER SERVER iceberg_hive_server;               
 
CREATE FOREIGN TABLE fdw_iceberg(id int, first_name text, last_name text, age int)
         SERVER iceberg_hive_server
         OPTIONS (resource 'test.customer');

INSERT INTO fdw_iceberg
	select
		generate_series(1,3000000) as id,
		md5(random()::text) as first_name, 
		md5(random()::text) as last_name,
		random() as age;   
       
SELECT * FROM fdw_iceberg;        
```


### Iceberg configuration

There are few options that help to configure working with Iceberg data source:

| Option                 | Description                                                                                                                                                                                                                                                      | The name of associated property in servers config file if relevant  |
|------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------|
| uri                    | Catalog uri                                                                                                                                                                                                                                                      | iceberg.param.uri                                                   |
| warehouse              | Data warehouse                                                                                                                                                                                                                                                   | iceberg.param.warehouse                                             |
| catalog_type           | Catalog type (required), current supported values are REST, HIVE_METASTORE                                                                                                                                                                                               | iceberg.config.catalog.type                                         |
| catalog_name           | Catalog name                                                                                                                                                                                                                                                     | iceberg.config.catalog.name                                         |
| ssl                    | Parameter to enable the working with secured connections. For some cases it's optional, for example, in case of rest catalog if you don't need to use custom trust store it's enough to define uri address started from **https**  to start working through ssl. | iceberg.config.ssl.enabled                                          |
| sslTrustStorePath      | The path to ssl trust store (Hive Metastore catalog only; for the REST catalog use `-Djavax.net.ssl.trustStore` in `PXF_JVM_OPTS`). If it's not defined and the value of **ssl** is **true**, connector uses JVM global trust store.                                                                                                                               | iceberg.config.ssl.truststore.path                                  |
| sslTrustStorePassword  | The password for ssl trust store. If it's not defined, connector uses JVM global trust store default password.                                                                                                                                                   | iceberg.config.ssl.truststore.password                              |
| ref                    | The name of iceberg branch or tag (when reading it should be created before usage)                                                                                                                                                                               | -//-                                                                |
| fragmentSize           | The target size of each fragment in bytes (it is just guidance and the actual split size may be different)                                                                                                                                                       | -//-                                                                |
| s3_endpoint            | S3 endpoint (in case if s3 is used to keep data)                                                                                                                                                                                                                 | iceberg.param.s3.endpoint                                           |
| s3_path_style_access   | S3 path style access (in case if s3 is used to keep data)                                                                                                                                                                                                        | iceberg.param.s3.path-style-access                                  |
| s3_access_key_id       | S3 access key id (in case if s3 is used to keep data)                                                                                                                                                                                                            | iceberg.param.s3.access-key-id                                      |
| s3_secret_access_key   | S3 secret access key (in case if s3 is used to keep data)                                                                                                                                                                                                        | iceberg.param.s3.secret-access-key                                  |
| aws_region             | AWS region (in case if s3 is used to keep data)                                                                                                                                                                                                                  | iceberg.param.client.region                                         |

