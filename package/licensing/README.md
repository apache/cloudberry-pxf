<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

# Binary distribution licensing

## Why the source and binary compliance files differ

The source release contains PXF's own code, so the repository root `LICENSE`
and `NOTICE` describe exactly that.

The convenience binary packages are a different artifact. `make stage` builds
the PXF server into a Spring Boot application JAR
(`application/pxf-app-<version>.jar`) that bundles its **entire runtime
classpath** under `BOOT-INF/lib` — Hadoop, Hive, HBase, Parquet, ORC, Avro, the
AWS and Azure SDKs, Spring, embedded Tomcat, and everything those pull in.
None of that is in the source tree, and its licenses are not all Apache-2.0.

So the binary packages ship a separate pair, following the same convention as
Apache Spark, Apache Kafka and the Apache Cloudberry main repository:

| in the repository    | installed in the package as |
|----------------------|-----------------------------|
| `LICENSE-binary`     | `LICENSE`                   |
| `NOTICE-binary`      | `NOTICE`                    |
| `licenses-binary/`   | `licenses/`                 |
| `DISCLAIMER`         | `DISCLAIMER`                |

`make stage` (used by `make tar` and `make rpm`) and `make deb` both install
them and **fail** if any is missing.

## The inventory

`bundled-components.tsv` is the curated inventory — one row per JAR that ends
up inside the application JAR:

```
jar_file <TAB> coordinate <TAB> license_id <TAB> license_file <TAB> notes
```

* `license_file` is `-` for plain Apache-2.0 components, which the Apache
  license text at the top of `LICENSE-binary` already covers. Everything else
  names a file under `licenses-binary/`.
* Third-party rows name the **exact** JAR file, version included, so that a
  dependency bump fails the check and forces the license to be re-reviewed.
  PXF's own modules use a glob (`pxf-api-*.jar`) because their version tracks
  the release.

## Regenerating and checking

```bash
# build the server first so the application JAR exists
make -C server stage-notest

# rewrite LICENSE-binary and NOTICE-binary from the JAR + inventory
python3 package/licensing/generate-binary-license.py --generate

# verify the checked-in files still match what was built (CI)
python3 package/licensing/generate-binary-license.py --check
```

`--check` fails when:

* a JAR appears in or disappears from the application JAR without a matching
  inventory update;
* a component's license text is missing from `licenses-binary/`, or a file
  there is no longer referenced;
* the `LICENSE ?=` tag in the root `Makefile` (the RPM `License:` field) no
  longer covers every license in the inventory;
* `LICENSE-binary` or `NOTICE-binary` differ from what would be generated;
* an ASF [Category X](https://www.apache.org/legal/resolved.html#category-x)
  license shows up. Such a component cannot ship in an Apache release at all,
  and listing it in `LICENSE-binary` would only document the violation — the
  dependency has to go.

## Adding or bumping a dependency

1. Build the server and run `--check`; it tells you which JAR is unaccounted for.
2. Determine the component's license. The most reliable sources, in order:
   its `META-INF/LICENSE*` and `META-INF/NOTICE*` inside the JAR, then the
   `<licenses>` block in its published POM, then the upstream repository at the
   tag matching the version.
3. Add the row to `bundled-components.tsv`. For anything that is not plain
   Apache-2.0, add its license text to `licenses-binary/`.
4. Re-run with `--generate` and commit the regenerated files.

Watch for components that bundle others — `org.postgresql:postgresql` shades
com.ongres SCRAM/stringprep, and `com.google.cloud.bigdataoss:gcs-connector`
ships as a shaded uber-JAR. Their embedded notices have to be accounted for too.
