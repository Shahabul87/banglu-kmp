#!/bin/sh
# Copies the compiled dictionary into the installer resources (gitignored).
set -e
cd "$(dirname "$0")"
mkdir -p resources/common
cp ../dictionary.sqlite resources/common/dictionary.sqlite
echo "dictionary staged: $(du -h resources/common/dictionary.sqlite | cut -f1)"
