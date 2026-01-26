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