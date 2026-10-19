#!/bin/sh
# UBEOD001 日次利息積数バッチ (Java 版) 実行スクリプト (JDK 8 と Maven が必要)
#   使い方: batch/java/run.sh [ACCOUNTS.DAT へのパス]   (既定: batch/golden/baseline/ACCOUNTS.DAT。相対パスは呼び出し元基準)
#   出力  : batch/java/work/ACCRUED.DAT, batch/java/work/UBEOD001.LOG
#   stdout: プログラムの出力 + "RC=<RC>" + "GOLDEN OK: …" / "GOLDEN MISMATCH: …" (baseline の期待値と比較)
#   終了コード: 一致 → RC (0 / 8 / 12)、不一致 → 1、起動失敗 → 2
#   batch/cobol/run.sh と違い set -e は使わない (RC=8・12 でも RC= と比較まで出す。仕様書 E25)
#   設計: docs/migration/UBEOD001-java-design.md §3.1
JAVA_DIR=$(cd "$(dirname "$0")" && pwd)
INPUT=${1:-$JAVA_DIR/../golden/baseline/ACCOUNTS.DAT}
mkdir -p "$JAVA_DIR/work" && cp "$INPUT" "$JAVA_DIR/work/ACCOUNTS.DAT" || exit 2
cd "$JAVA_DIR/work" || exit 2
rm -f ACCRUED.DAT UBEOD001.LOG
"$JAVA_DIR/ubeod001.sh"; RC=$?
[ "$RC" -eq 2 ] && exit 2
echo "RC=$RC"
BASE=$JAVA_DIR/../golden/baseline
if cmp -s ACCRUED.DAT "$BASE/ACCRUED.DAT"; then
  echo "GOLDEN OK: ACCRUED.DAT matches golden/baseline/ACCRUED.DAT"
else
  echo "GOLDEN MISMATCH: ACCRUED.DAT differs from golden/baseline/ACCRUED.DAT"; diff ACCRUED.DAT "$BASE/ACCRUED.DAT"; exit 1
fi
exit $RC
