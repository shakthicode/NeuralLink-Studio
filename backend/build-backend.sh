#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
g++ -std=c++17 -O2 -Wall -Wextra -pedantic simulation_backend.cpp -o simulation_backend
echo "Backend built successfully: backend/simulation_backend"
