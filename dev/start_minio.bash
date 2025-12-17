#!/bin/bash

set -e

WORKSPACE_DIR=${WORKSPACE_DIR:-/home/gpadmin/workspace}
MINIO_BIN=${WORKSPACE_DIR}/minio
MC_BIN=${WORKSPACE_DIR}/mc
MINIO_DATA_DIR=${MINIO_DATA_DIR:-${WORKSPACE_DIR}/minio-data}
MINIO_PORT=${MINIO_PORT:-9000}
MINIO_CONSOLE_PORT=${MINIO_CONSOLE_PORT:-9001}

export MINIO_ROOT_USER=${MINIO_ROOT_USER:-admin}
export MINIO_ROOT_PASSWORD=${MINIO_ROOT_PASSWORD:-password}

echo "MinIO credentials: rootUser=${MINIO_ROOT_USER} rootPassword=${MINIO_ROOT_PASSWORD}"

mkdir -p ${MINIO_DATA_DIR}

echo "Starting MinIO server on port ${MINIO_PORT}..."
${MINIO_BIN} server ${MINIO_DATA_DIR} \
  --address ":${MINIO_PORT}" \
  --console-address ":${MINIO_CONSOLE_PORT}" &

MINIO_PID=$!
echo "MinIO started with PID: ${MINIO_PID}"

sleep 3

echo "Creating test bucket 'gpdb-ud-scratch'..."
${MC_BIN} alias set local http://localhost:${MINIO_PORT} ${MINIO_ROOT_USER} ${MINIO_ROOT_PASSWORD}
${MC_BIN} mb local/gpdb-ud-scratch --ignore-existing

export PROTOCOL=minio
export ACCESS_KEY_ID=${MINIO_ROOT_USER}
export SECRET_ACCESS_KEY=${MINIO_ROOT_PASSWORD}

echo "MinIO is ready!"
echo "  Console: http://localhost:${MINIO_CONSOLE_PORT}"
echo "  API: http://localhost:${MINIO_PORT}"
