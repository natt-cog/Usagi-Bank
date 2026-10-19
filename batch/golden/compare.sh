#!/bin/bash
# UBEOD001 新旧比較ハーネス (COBOL 版 ↔ Java 版)
#   使い方: ./compare.sh [--require-java] [CASE...]
#     引数なし       : 全ケースを COBOL 版と Java 版で同じ入力から実行し、ケースごとの一致 / 不一致を表で出す
#     CASE...        : 指定したケースだけ実行する
#     --require-java : Java 版が未実装なら終了コード 3 にする (p3・Jenkins ではこれを付ける)
#   比べるもの (golden-lib.sh の比較処理をそのまま使う。ACCRUED.DAT・UBEOD001.LOG・stdout・RC のバイト比較):
#     COBOL    = COBOL 版の実行結果 ↔ 期待値
#     Java     = Java 版の実行結果  ↔ 期待値
#     新旧     = Java 版の実行結果  ↔ COBOL 版の実行結果
#   Java 版がまだない (src/main/java/jp/usagi/bank/batch/host/Ubeod001.java がない) ときは
#   Java・新旧の列を「未実装」とし、COBOL ↔ 期待値の比較だけで判定する。
#   COBOL 版は run-cobol.sh と同じく cobc でビルドした実行ファイルを直接呼ぶ
#   (batch/cobol/run.sh は set -e のため RC≠0 で比較まで進まない。仕様書 E25)。
#   終了コード: 0 = 不一致なし, 1 = 不一致あり, 2 = コンパイル・ビルド失敗など,
#               3 = --require-java を付けたのに Java 版が未実装 (不一致があれば 1 を優先)
#   作業ディレクトリ: batch/golden/work-compare/{cobol,java}/ (git 管理外)
#   必要なもの: GnuCOBOL の cobc、JDK 8 と Maven (Java 版があるとき)
set -u
GOLDEN_DIR=$(cd "$(dirname "$0")" && pwd)
COMPARE_WORK=${COMPARE_WORK:-$GOLDEN_DIR/work-compare}
COBOL_WORK=$COMPARE_WORK/cobol
JAVA_WORK=$COMPARE_WORK/java
ROOT=$(cd "$GOLDEN_DIR/../.." && pwd)
JAVA_CMD=$ROOT/batch/java/ubeod001.sh
JAVA_SRC=$ROOT/src/main/java/jp/usagi/bank/batch/host/Ubeod001.java
. "$GOLDEN_DIR/golden-lib.sh"

cases="" require_java=0
for a in "$@"; do
  case $a in
    --require-java) require_java=1 ;;
    --update) echo "compare.sh: --update は使えない (期待値は run-cobol.sh --update で作る)" >&2; exit 2 ;;
    -*) echo "使い方: $0 [--require-java] [CASE...]" >&2; exit 2 ;;
    *) cases="$cases $a" ;;
  esac
done
[ -n "$cases" ] || cases=$(golden_cases)

rm -rf "$COMPARE_WORK"; mkdir -p "$COBOL_WORK" "$JAVA_WORK"
cobc -x -o "$COMPARE_WORK/ubeod001" "$ROOT/batch/cobol/UBEOD001.cbl" || exit 2
java_impl=0
if [ -f "$JAVA_SRC" ]; then
  "$JAVA_CMD" --build-only || exit 2
  java_impl=1
fi

# compare_dirs EXPECTED_ROOT CASE ACTUAL_DIR DIFF_FILE  -> "一致" / "不一致"
compare_dirs() {
  if ( GOLDEN_DIR=$1; golden_compare_case "$2" "$3" ) >"$4"; then
    echo 一致; return 0
  fi
  echo 不一致; return 1
}

rc_of() { cat "$1/rc.txt" 2>/dev/null || echo -; }

n=0 ng_cobol=0 ng_java=0 ng_cross=0 details=""
echo "| ケース | 期待 RC | COBOL RC | COBOL ↔ 期待値 | Java RC | Java ↔ 期待値 | 新旧 (Java ↔ COBOL) |"
echo "|---|---|---|---|---|---|---|"
for case in $cases; do
  n=$((n + 1))
  if [ ! -d "$GOLDEN_DIR/$case" ]; then
    echo "| \`$case\` | - | - | ケースなし | - | - | - |"
    ng_cobol=$((ng_cobol + 1)); continue
  fi
  exp_rc=$(rc_of "$GOLDEN_DIR/$case")

  golden_run_case "$case" "$COBOL_WORK/$case" "$COMPARE_WORK/ubeod001"
  c=$(compare_dirs "$GOLDEN_DIR" "$case" "$COBOL_WORK/$case" "$COBOL_WORK/$case.diff") || {
    ng_cobol=$((ng_cobol + 1))
    details="$details$case COBOL ↔ 期待値:"$'\n'"$(cat "$COBOL_WORK/$case.diff")"$'\n'
  }

  if [ $java_impl = 1 ]; then
    golden_run_case "$case" "$JAVA_WORK/$case" "$JAVA_CMD"
    j_rc=$(rc_of "$JAVA_WORK/$case")
    j=$(compare_dirs "$GOLDEN_DIR" "$case" "$JAVA_WORK/$case" "$JAVA_WORK/$case.diff") || {
      ng_java=$((ng_java + 1))
      details="$details$case Java ↔ 期待値:"$'\n'"$(cat "$JAVA_WORK/$case.diff")"$'\n'
    }
    x=$(compare_dirs "$COBOL_WORK" "$case" "$JAVA_WORK/$case" "$JAVA_WORK/$case.cross.diff") || {
      ng_cross=$((ng_cross + 1))
      details="$details$case 新旧 (左=COBOL 右=Java):"$'\n'"$(sed 's/左=期待値 右=実行結果/左=COBOL 右=Java/' "$JAVA_WORK/$case.cross.diff")"$'\n'
    }
  else
    j_rc=- j=未実装 x=未実装
  fi
  echo "| \`$case\` | $exp_rc | $(rc_of "$COBOL_WORK/$case") | $c | $j_rc | $j | $x |"
done

if [ -n "$details" ]; then
  echo
  echo "不一致の内容:"
  printf '%s' "$details"
fi
echo
echo "----"
echo "COBOL ↔ 期待値: 一致=$((n - ng_cobol)) 不一致=$ng_cobol (全 $n ケース)"
if [ $java_impl = 1 ]; then
  echo "Java ↔ 期待値 : 一致=$((n - ng_java)) 不一致=$ng_java (全 $n ケース)"
  echo "新旧          : 一致=$((n - ng_cross)) 不一致=$ng_cross (全 $n ケース)"
else
  echo "Java          : 未実装 (${JAVA_SRC#$ROOT/} がない。COBOL ↔ 期待値だけで判定)"
fi
[ $((ng_cobol + ng_java + ng_cross)) = 0 ] || exit 1
if [ $require_java = 1 ] && [ $java_impl = 0 ]; then
  echo "--require-java: Java 版が未実装のため終了コード 3"
  exit 3
fi
exit 0
