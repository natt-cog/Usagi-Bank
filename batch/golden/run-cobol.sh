#!/bin/bash
# UBEOD001 ゴールデンテスト (COBOL 版, GnuCOBOL cobc が必要)
#   使い方: ./run-cobol.sh [--update] [CASE...]
#     引数なし  : 全ケースを COBOL で実行し、期待値とバイト比較する
#     CASE...   : 指定したケースだけ実行する (例: ./run-cobol.sh baseline no-input)
#     --update  : 比較せず、COBOL の実行結果で期待値を作り直す
#   終了コード: 0 = 全ケース一致, 1 = 不一致あり, 2 = コンパイル失敗など
#   作業ディレクトリ: batch/golden/work/ (git 管理外)
set -u
GOLDEN_DIR=$(cd "$(dirname "$0")" && pwd)
GOLDEN_WORK=${GOLDEN_WORK:-$GOLDEN_DIR/work}
. "$GOLDEN_DIR/golden-lib.sh"

mkdir -p "$GOLDEN_WORK"
cobc -x -o "$GOLDEN_WORK/ubeod001" "$GOLDEN_DIR/../cobol/UBEOD001.cbl" || exit 2

golden_main "$@" -- "$GOLDEN_WORK/ubeod001"
