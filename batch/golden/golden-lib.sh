#!/bin/bash
# UBEOD001 ゴールデン比較の共通処理 (COBOL 版・Java 版で共用)
#
#   呼び出し側で golden_main を呼ぶ:
#     golden_main [--update] [CASE...] -- <UBEOD001 を実行するコマンド...>
#
#   各ケース <GOLDEN_DIR>/<CASE>/ について:
#     1. 空の作業ディレクトリを作り、ACCOUNTS.DAT があればコピーする
#        (ACCOUNTS.DAT がないケース = 入力ファイルなし)
#     2. 作業ディレクトリをカレントにしてコマンドを実行し、
#        stdout を stdout.txt、終了コードを rc.txt に保存する
#     3. ACCRUED.DAT / UBEOD001.LOG / stdout.txt / rc.txt を期待値とバイト比較する
#        (期待値ファイルがない = 出力されないことが期待値)
#   --update のときは比較せず、実行結果で期待値を置き換える。

GOLDEN_FILES="ACCRUED.DAT UBEOD001.LOG stdout.txt rc.txt"

golden_cases() {
  local d
  for d in "$GOLDEN_DIR"/*/; do
    d=$(basename "$d")
    case $d in
      work|work-*) ;;            # 作業ディレクトリ (work/・work-java/・work-compare/)
      *) echo "$d" ;;
    esac
  done
}

golden_run_case() {   # golden_run_case CASE WORKDIR CMD...
  local case=$1 dir=$2; shift 2
  rm -rf "$dir"; mkdir -p "$dir"
  if [ -f "$GOLDEN_DIR/$case/ACCOUNTS.DAT" ]; then
    cp "$GOLDEN_DIR/$case/ACCOUNTS.DAT" "$dir/ACCOUNTS.DAT"
  fi
  ( cd "$dir" && "$@" >stdout.txt 2>stderr.txt; echo $? >rc.txt )
}

golden_compare_case() {   # golden_compare_case CASE WORKDIR  -> 0: 一致
  local case=$1 dir=$2 f exp act ng=0
  for f in $GOLDEN_FILES; do
    exp="$GOLDEN_DIR/$case/$f"; act="$dir/$f"
    if [ -f "$exp" ] && [ -f "$act" ]; then
      if ! cmp -s "$exp" "$act"; then
        echo "    $f: 内容が違う (左=期待値 右=実行結果, cat -A 表示)"
        diff <(cat -A "$exp") <(cat -A "$act") | head -20 | sed 's/^/      /'
        ng=1
      fi
    elif [ -f "$exp" ]; then
      echo "    $f: 期待値にあるが出力されなかった"; ng=1
    elif [ -f "$act" ]; then
      echo "    $f: 期待値にないが出力された"; ng=1
    fi
  done
  if [ -s "$dir/stderr.txt" ]; then
    echo "    (参考) stderr に出力あり: $(head -c 200 "$dir/stderr.txt")"
  fi
  return $ng
}

golden_update_case() {   # golden_update_case CASE WORKDIR
  local case=$1 dir=$2 f
  for f in $GOLDEN_FILES; do
    if [ -f "$dir/$f" ]; then
      cp "$dir/$f" "$GOLDEN_DIR/$case/$f"
    else
      rm -f "$GOLDEN_DIR/$case/$f"
    fi
  done
}

golden_main() {
  local update=0 cases="" case ok=0 ng=0 rc
  while [ $# -gt 0 ]; do
    case $1 in
      --update) update=1 ;;
      --) shift; break ;;
      *) cases="$cases $1" ;;
    esac
    shift
  done
  [ $# -gt 0 ] || { echo "golden_main: 実行コマンドがない" >&2; return 2; }
  [ -n "$cases" ] || cases=$(golden_cases)
  for case in $cases; do
    if [ ! -d "$GOLDEN_DIR/$case" ]; then
      echo "NG   $case (ケースのディレクトリがない)"; ng=$((ng + 1)); continue
    fi
    golden_run_case "$case" "$GOLDEN_WORK/$case" "$@"
    rc=$(cat "$GOLDEN_WORK/$case/rc.txt")
    if [ $update = 1 ]; then
      golden_update_case "$case" "$GOLDEN_WORK/$case"
      echo "UPD  $case (RC=$rc)"; ok=$((ok + 1))
    elif golden_compare_case "$case" "$GOLDEN_WORK/$case" >"$GOLDEN_WORK/$case.diff"; then
      echo "OK   $case (RC=$rc)"; ok=$((ok + 1))
    else
      echo "NG   $case (RC=$rc)"; cat "$GOLDEN_WORK/$case.diff"; ng=$((ng + 1))
    fi
  done
  echo "----"
  if [ $update = 1 ]; then
    echo "期待値を更新: $ok ケース"
  else
    echo "結果: OK=$ok NG=$ng (全 $((ok + ng)) ケース)"
  fi
  [ $ng = 0 ]
}
