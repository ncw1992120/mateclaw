#!/bin/sh
set -eu
mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"
mc mb --ignore-existing "local/${MINIO_BUCKET:-mateclaw-sim}"
mc anonymous set none "local/${MINIO_BUCKET:-mateclaw-sim}"
echo 'MinIO bucket ready'
