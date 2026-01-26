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
