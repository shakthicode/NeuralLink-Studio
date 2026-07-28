#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
command -v java >/dev/null
command -v mvn >/dev/null
command -v g++ >/dev/null
mvn clean compile javafx:run
