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

# Running Automation in Docker

## Prerequisites

Before running the automation tests, ensure you have:

* Docker and Docker Compose installed
* Both `cloudberry-pxf` and `cloudberry` repositories cloned in the same parent directory (they will be mounted into the Docker container)

## Running Automation Tests

1. Navigate to the `cloudberry-pxf` directory:
   ```bash
   cd cloudberry-pxf
   ```

2. Stop and remove any existing containers and volumes:
   ```bash
   docker compose -f ci/docker/pxf-cbdb-dev/ubuntu/docker-compose.yml down -v
   ```

3. Build the Docker images:
   ```bash
   docker compose -f ci/docker/pxf-cbdb-dev/ubuntu/docker-compose.yml build
   ```

4. Start the containers in detached mode:
   ```bash
   docker compose -f ci/docker/pxf-cbdb-dev/ubuntu/docker-compose.yml up -d
   ```

5. Run the entrypoint script to set up the environment:
   ```bash
   docker exec pxf-cbdb-dev bash -lc \
      "cd /home/gpadmin/workspace/cloudberry-pxf/ci/docker/pxf-cbdb-dev/ubuntu && ./script/entrypoint.sh"
   ```

6. Execute the test suite:
   ```bash
   docker exec pxf-cbdb-dev bash -lc \
      "cd /home/gpadmin/workspace/cloudberry-pxf/ci/docker/pxf-cbdb-dev/ubuntu && ./script/run_tests.sh"
   ```
   You can run tests multiple times in one container.

## Troubleshooting
When something went wrong:

Jump into container: `docker compose ps` + `docker exec -it <id> bash`

Check logs:

* **PXF logs**: `/home/gpadmin/pxf-base/logs/`
* **Hadoop logs**: `/home/gpadmin/workspace/singlecluster/storage/logs/`