#!/bin/sh
set -eu
mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"
# Copy each fixture so the host-mounted directory name is not added to the
# object key; ObjectRef examples can use a stable `files/<name>` path.
for fixture in /seed/*; do
  [ "$(basename "$fixture")" = "fixtures-manifest.json" ] && continue
  mc cp "$fixture" "local/${MINIO_BUCKET:-mateclaw-sim}/files/"
done
echo 'Fixture files uploaded'
