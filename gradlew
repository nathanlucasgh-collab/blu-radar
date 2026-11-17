#!/usr/bin/env sh
if [ -x "$(command -v gradle)" ]; then
  exec gradle "$@"
else
  echo "Gradle is not installed or on PATH. Install Android Studio or Gradle to build this project."
  exit 1
fi
