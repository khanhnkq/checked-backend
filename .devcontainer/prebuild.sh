#!/usr/bin/env bash
set -euo pipefail

chmod +x ./gradlew
./gradlew --no-daemon testClasses
