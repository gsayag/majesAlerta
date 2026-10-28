#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
echo "Abre http://127.0.0.1:8000 en tu navegador."
python3 server.py
