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
# Docker container for Cloudberry development/testing

## Requirements

- docker 1.13 (with 4+ GB allocated for docker host)

## Local Development with Docker

The directory `ci/docker/pxf-cbdb-dev/ubuntu` contains the necessary configuration to set up a local development environment using Docker Compose. This environment includes Cloudberry and a single-node Hadoop cluster.

### Building the Image

To build the development image:

```bash
cd ci/docker/pxf-cbdb-dev/ubuntu
docker compose build
```

### Running the Environment

To start the environment:

```bash
docker compose up -d
```

### Running Tests

Once the containers are running, you can execute tests as described in `automation/README.Docker.md`.
