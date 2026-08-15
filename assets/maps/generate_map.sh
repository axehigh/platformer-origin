#!/usr/bin/env bash
set -euo pipefail
MAPS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$MAPS_DIR"
PY="${PYTHON:-/usr/bin/python3}"
exec "$PY" "$MAPS_DIR/../../.junie/skills/tmx-map-generator/scripts/generate_tmx.py" "$@"
