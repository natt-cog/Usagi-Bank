#!/bin/sh
# UBEOD001 日次利息積数バッチ 実行スクリプト (GnuCOBOL 3.x)
#   使い方: ./run.sh [ACCOUNTS.DAT へのパス]
#   出力  : work/ACCRUED.DAT, work/UBEOD001.LOG
set -e
cd "$(dirname "$0")"
INPUT="${1:-data/ACCOUNTS.DAT}"
mkdir -p work
cobc -x -o work/ubeod001 UBEOD001.cbl
cp "$INPUT" work/ACCOUNTS.DAT
cd work && ./ubeod001
RC=$?
echo "RC=$RC"
if [ -f ../expected/ACCRUED.DAT ]; then
  if diff -q ACCRUED.DAT ../expected/ACCRUED.DAT >/dev/null; then
    echo "GOLDEN OK: ACCRUED.DAT matches expected/ACCRUED.DAT"
  else
    echo "GOLDEN MISMATCH:"; diff ACCRUED.DAT ../expected/ACCRUED.DAT || true; exit 1
  fi
fi
exit $RC
