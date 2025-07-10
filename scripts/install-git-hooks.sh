#!/bin/sh

set -eu

repository_root=$(git rev-parse --show-toplevel)
cd "$repository_root"

configured_hooks_path=$(git config --get core.hooksPath || true)

case "$configured_hooks_path" in
    "")
        git config core.hooksPath .githooks
        echo "core.hooksPath를 .githooks로 설정했습니다."
        ;;
    .githooks)
        echo "core.hooksPath가 이미 .githooks로 설정되어 있습니다."
        ;;
    *)
        echo "기존 core.hooksPath '$configured_hooks_path'를 유지합니다. .githooks를 사용하려면 충돌을 먼저 해결해 주세요." >&2
        exit 1
        ;;
esac
