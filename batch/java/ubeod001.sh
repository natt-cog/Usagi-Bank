#!/bin/bash
# UBEOD001 日次利息積数バッチ (Java 版) の起動コマンド
#   使い方:
#     batch/java/ubeod001.sh               カレントディレクトリで UBEOD001 を実行する
#     batch/java/ubeod001.sh --build-only  ビルドだけして終了する (run-java.sh が最初に 1 回呼ぶ)
#   入力 : ./ACCOUNTS.DAT              出力: ./ACCRUED.DAT, ./UBEOD001.LOG
#   stdout: UBEOD001 の DISPLAY メッセージだけ (Maven などの出力は stderr)
#   終了コード: UBEOD001 の RC (0 / 8 / 12)、起動失敗 (ビルド失敗・JDK 不一致・未実装) は 2
#   設計: docs/migration/UBEOD001-java-design.md §3.1
set -u
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
MAIN=jp.usagi.bank.batch.host.Ubeod001
SRC=$ROOT/src/main/java/jp/usagi/bank/batch/host/Ubeod001.java
CLASS=$ROOT/target/classes/jp/usagi/bank/batch/host/Ubeod001.class
CP_FILE=$ROOT/target/ubeod001.classpath

if [ -n "${JAVA_HOME:-}" ]; then JAVA=$JAVA_HOME/bin/java; else JAVA=java; fi

build() {
  ( cd "$ROOT" && mvn -B -q -DskipTests compile dependency:build-classpath \
      -Dmdep.includeScope=runtime -Dmdep.outputFile=target/ubeod001.classpath ) >&2 \
    || { echo "ubeod001.sh: ビルドに失敗した" >&2; return 1; }
}

check_jdk() {
  local v
  v=$("$JAVA" -version 2>&1 | head -1)
  case $v in
    *'"1.8'*) return 0 ;;
    *) echo "ubeod001.sh: JDK 8 が必要 ($JAVA: ${v:-起動できない})。JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 を設定する" >&2
       return 1 ;;
  esac
}

case $# in
  0) ;;
  1) if [ "$1" = --build-only ]; then
       check_jdk || exit 2
       build || exit 2
       exit 0
     fi
     echo "使い方: $0 [--build-only]" >&2; exit 2 ;;
  *) echo "使い方: $0 [--build-only]" >&2; exit 2 ;;
esac

check_jdk || exit 2
if [ ! -f "$SRC" ]; then
  echo "ubeod001.sh: Java 版 UBEOD001 は未実装 (${SRC#$ROOT/} がない)" >&2
  exit 2
fi
if [ ! -f "$CLASS" ] || [ ! -f "$CP_FILE" ]; then
  build || exit 2
fi
exec "$JAVA" -cp "$ROOT/target/classes:$(cat "$CP_FILE")" "$MAIN"
