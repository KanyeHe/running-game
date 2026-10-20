#!/bin/sh
# 从仓库根目录统一验证；调用前将 JAVA_HOME/PATH 指向 JDK 25。
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"
if [ -n "${JAVA_HOME:-}" ]; then
  PATH="$JAVA_HOME/bin:$PATH"
  export PATH
fi
npm --prefix web ci --ignore-scripts --no-audit --no-fund
java scripts/CheckMethodLength.java server/src scripts/CheckMethodLength.java deploy
node scripts/check-method-length.cjs client/assets/scripts web/src web/scripts web/tests scripts/check-method-length.cjs scripts/test-client.cjs
npm --prefix web run build
npm --prefix web test
node scripts/test-client.cjs ../web/node_modules/typescript
sh server/mvnw -B -ntp verify
