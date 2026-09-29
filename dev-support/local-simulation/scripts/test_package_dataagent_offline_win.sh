#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd)"
TEST_TMP="$(mktemp -d "${TMPDIR:-/tmp}/test-dataagent-offline.XXXXXX")"
trap 'rm -rf "$TEST_TMP"' EXIT
mkdir -p "$TEST_TMP/bin" "$TEST_TMP/worker/src/runner" "$TEST_TMP/output"
printf 'fake jar\n' > "$TEST_TMP/app.jar"
printf 'worker source\n' > "$TEST_TMP/worker/src/runner/worker.py"

cat > "$TEST_TMP/bin/uv" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
out=""
while (($#)); do
  if [[ "$1" == "--output-file" ]]; then out="$2"; shift 2; else shift; fi
done
printf 'pydantic==2.13.5\n' > "$out"
SH
cat > "$TEST_TMP/bin/python3" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
[[ "$1" == "-m" && "$2" == "pip" && "$3" == "download" ]]
while (($#)); do
  if [[ "$1" == "--dest" ]]; then mkdir -p "$2"; printf 'wheel fixture\n' > "$2/pydantic-2.13.5-py3-none-any.whl"; shift 2; else shift; fi
done
SH
chmod +x "$TEST_TMP/bin/uv" "$TEST_TMP/bin/python3"

DATAAGENT_JAR="$TEST_TMP/app.jar" \
DATAAGENT_OFFLINE_PACKAGE_OUTPUT="$TEST_TMP/output/package.zip" \
UV_CMD="$TEST_TMP/bin/uv" \
PYTHON_COMMAND="$TEST_TMP/bin/python3" \
PATH="$TEST_TMP/bin:$PATH" \
  bash "$ROOT_DIR/dev-support/package-dataagent-offline-win.sh"

unzip -t "$TEST_TMP/output/package.zip" >/dev/null
unzip -Z1 "$TEST_TMP/output/package.zip" > "$TEST_TMP/archive-entries.txt"
grep -Fxq 'DataAgent/mateclaw-dataagent-1.0.0-SNAPSHOT.jar' "$TEST_TMP/archive-entries.txt"
grep -Fxq 'DataAgent/python-worker/src/runner/worker.py' "$TEST_TMP/archive-entries.txt"
grep -Fxq 'DataAgent/python-worker/requirements.txt' "$TEST_TMP/archive-entries.txt"
grep -Fxq 'DataAgent/python-worker/wheelhouse/pydantic-2.13.5-py3-none-any.whl' "$TEST_TMP/archive-entries.txt"
echo "Windows 离线包结构和 ZIP 完整性检查通过。"
