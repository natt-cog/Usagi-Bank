#!/bin/bash
# UBEOD001 ゴールデンテスト (Java 版, JDK 8 と Maven が必要)
#   使い方: ./run-java.sh [CASE...]      (終了コードは run-cobol.sh と同じ)
#     引数なし  : 全ケースを Java 版 (batch/java/ubeod001.sh) で実行し、期待値とバイト比較する
#     CASE...   : 指定したケースだけ実行する
#     --update  : 動くが使わない (期待値は COBOL の結果だけで作る。README 参照)
#   終了コード: 0 = 全ケース一致, 1 = 不一致あり, 2 = ビルド失敗など
#   作業ディレクトリ: batch/golden/work-java/ (git 管理外)
set -u
GOLDEN_DIR=$(cd "$(dirname "$0")" && pwd)
GOLDEN_WORK=${GOLDEN_WORK:-$GOLDEN_DIR/work-java}
. "$GOLDEN_DIR/golden-lib.sh"

"$GOLDEN_DIR/../java/ubeod001.sh" --build-only || exit 2     # 1 回だけビルド (ケースごとにはビルドしない)
golden_main "$@" -- "$GOLDEN_DIR/../java/ubeod001.sh"
