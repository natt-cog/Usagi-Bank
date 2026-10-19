#!/bin/bash
# UBEOD001 ファイル連携の結合テスト (アプリ ↔ COBOL 版 / Java 版)
#   使い方: batch/golden/integration.sh
#   実際の連携経路 (アプリの API → ホストバッチ → アプリの API) を新旧両方で通し、取込結果が同じであることを確かめる。
#     手順 1: アプリを起動し、GET /api/batch/accounts-file (HTTP Basic, admin) で ACCOUNTS.DAT を取得する
#     手順 2: 同じ ACCOUNTS.DAT を batch/cobol/run.sh と batch/java/run.sh に流し、
#             ACCRUED.DAT・UBEOD001.LOG・stdout・RC がバイト一致することを確かめる
#     手順 3: Java 版の ACCRUED.DAT を POST /api/batch/accrued-file で取り込み、取込後の状態を記録する
#             (更新件数、ACCOUNTS.DAT の再取得で見た各口座の未払利息、GET /api/accounts)
#     手順 4: アプリを再起動し (H2 は初期化される)、ACCOUNTS.DAT が手順 1 と同じであることを確かめてから
#             COBOL 版の ACCRUED.DAT で手順 3 と同じことをする
#     手順 5: 新旧の取込結果 (更新件数・各口座の未払利息・GET /api/accounts) が一致することを確かめ、
#             取込前後の未払利息の増分の合計が UBEOD001.LOG の当日利息合計と同じであることも確かめる
#   未払利息は GET /api/accounts の JSON (AccountDto) に含まれないので、取込後に ACCOUNTS.DAT を再取得して見る。
#   取込後の再取得ファイルは、行末空白を除くと取り込んだ ACCRUED.DAT と同じになるはず (未払利息・トレーラ合計を含む)。
#   batch/cobol/run.sh・batch/java/run.sh の「GOLDEN OK / MISMATCH」は golden/baseline との比較で、
#   API の ACCOUNTS.DAT は処理日 (ヘッダ) が baseline と違うため MISMATCH (終了コード 1) になる。判定には使わず、
#   代わりに「baseline の期待値の処理日を差し替えたもの」と一致するかを参考として出す。
#   終了コード: 0 = 新旧一致, 1 = 不一致あり, 2 = 起動失敗など (ポート使用中、ビルド失敗、API エラー)
#   作業ディレクトリ: batch/golden/work-integration/ (git 管理外。アプリのログ app-*.log もここ)
#   必要なもの: JDK 8 (JAVA_HOME)、Maven、GnuCOBOL の cobc、curl、jq、bc。ポート 8080 が空いていること
set -u
GOLDEN_DIR=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$GOLDEN_DIR/../.." && pwd)
WORK=${INTEGRATION_WORK:-$GOLDEN_DIR/work-integration}
PORT=8080
BASE_URL=http://localhost:$PORT/usagi
AUTH=admin:admin123
START_TIMEOUT=${START_TIMEOUT:-300}
COBOL_RUN=$ROOT/batch/cobol/run.sh
JAVA_RUN=$ROOT/batch/java/run.sh
export LC_ALL=C

APP_PID=""
NG=0

log() { printf '%s\n' "$*"; }
fail() { log "ERROR: $*" >&2; exit 2; }
ng() { log "NG: $*"; NG=1; }
ok() { log "OK: $*"; }

port_in_use() { lsof -iTCP:$PORT -sTCP:LISTEN -t >/dev/null 2>&1; }

start_app() {   # start_app LABEL
  port_in_use && fail "ポート $PORT が使用中 (先にアプリを止める: lsof -iTCP:$PORT -sTCP:LISTEN)"
  log "アプリを起動する (mvn spring-boot:run、ログ: ${WORK#$ROOT/}/app-$1.log)"
  ( cd "$ROOT" && exec setsid mvn -B -q spring-boot:run ) >"$WORK/app-$1.log" 2>&1 &
  APP_PID=$!
  local i=0
  until [ "$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/login")" = 200 ]; do
    kill -0 "$APP_PID" 2>/dev/null || fail "アプリが起動しなかった (${WORK#$ROOT/}/app-$1.log を見る)"
    i=$((i + 1)); [ $i -le "$START_TIMEOUT" ] || fail "アプリが ${START_TIMEOUT} 秒で起動しなかった"
    sleep 1
  done
  log "起動した ($(grep -o 'Started UsagiBankApplication in [0-9.]* seconds' "$WORK/app-$1.log" | head -1))"
}

stop_app() {
  [ -n "$APP_PID" ] || return 0
  kill -TERM -- "-$APP_PID" 2>/dev/null || kill -TERM "$APP_PID" 2>/dev/null
  local i=0
  while kill -0 "$APP_PID" 2>/dev/null || port_in_use; do
    i=$((i + 1)); [ $i -le 60 ] || { kill -KILL -- "-$APP_PID" 2>/dev/null; break; }
    sleep 1
  done
  APP_PID=""
  log "アプリを停止した"
}
trap stop_app EXIT
trap 'exit 2' INT TERM

api_get() {   # api_get PATH OUT
  local code
  code=$(curl -s -u "$AUTH" -o "$2" -w '%{http_code}' "$BASE_URL$1") || fail "GET $1 に失敗した"
  [ "$code" = 200 ] || fail "GET $1 が HTTP $code"
}

api_import() {   # api_import FILE OUT
  local code
  code=$(curl -s -u "$AUTH" -o "$2" -w '%{http_code}' -F "file=@$1" "$BASE_URL/api/batch/accrued-file") \
    || fail "POST /api/batch/accrued-file に失敗した"
  [ "$code" = 200 ] || { cat "$2" >&2; fail "POST /api/batch/accrued-file が HTTP $code"; }
}

# 明細ごとに "店番-口座番号 未払利息(円)" を出す
accrued_of() {
  awk 'substr($0,1,1)=="D" { a=substr($0,36,15); sub(/^0+/,"",a); if (a=="") a="0";
       printf "%s-%s %s.%s\n", substr($0,2,3), substr($0,5,7), a, substr($0,51,2) }' "$1"
}

# run_batch NAME RUN_SH OUTDIR: run.sh の stdout を run.out に取り、プログラムの stdout・RC・出力ファイルを OUTDIR に揃える
run_batch() {
  local name=$1 run=$2 out=$3 src rc
  src=$(dirname "$run")/work
  mkdir -p "$out" "$src"
  rm -f "$src/ACCRUED.DAT" "$src/UBEOD001.LOG"
  "$run" "$WORK/ACCOUNTS.DAT" >"$out/run.out" 2>"$out/run.err"; rc=$?
  log "\$ ${run#$ROOT/} ${WORK#$ROOT/}/ACCOUNTS.DAT   (終了コード $rc)"
  sed 's/^/    /' "$out/run.out"
  [ "$rc" -eq 2 ] && { cat "$out/run.err" >&2; fail "$name 版を起動できなかった"; }
  if grep -q '^RC=' "$out/run.out"; then
    sed -n 's/^RC=//p' "$out/run.out" | tail -1 >"$out/rc.txt"
    sed '/^RC=/,$d' "$out/run.out" >"$out/stdout.txt"
  else   # batch/cobol/run.sh は set -e のため RC≠0 だと RC= を出さずに終わる (仕様書 E25)
    echo "$rc" >"$out/rc.txt"
    cp "$out/run.out" "$out/stdout.txt"
  fi
  for f in ACCRUED.DAT UBEOD001.LOG; do [ -f "$src/$f" ] && cp "$src/$f" "$out/"; done
  [ "$(cat "$out/rc.txt")" = 0 ] || ng "$name 版の RC が 0 でない (RC=$(cat "$out/rc.txt"))"
  [ -f "$out/ACCRUED.DAT" ] || ng "$name 版が ACCRUED.DAT を出力しなかった"
}

# import_leg NAME ACCRUED OUTDIR: 取込と取込後の状態の記録・検証
import_leg() {
  local name=$1 accrued=$2 out=$3 n
  mkdir -p "$out"
  api_import "$accrued" "$out/import.json"
  log "POST /api/batch/accrued-file ($name 版の ACCRUED.DAT) → $(cat "$out/import.json")"
  api_get /api/batch/accounts-file "$out/ACCOUNTS-after.DAT"
  api_get /api/accounts "$out/accounts.json"
  jq -S . "$out/accounts.json" >"$out/accounts.sorted.json"
  accrued_of "$out/ACCOUNTS-after.DAT" >"$out/accrued-after.txt"
  n=$(grep -c '^D' "$accrued")
  [ "$(jq -r .updated "$out/import.json")" = "$n" ] && [ "$(jq -r .skipped "$out/import.json")" = 0 ] \
    && ok "$name: 更新件数 $n = ACCRUED.DAT の明細件数、スキップ 0" \
    || ng "$name: 更新件数・スキップ件数が ACCRUED.DAT の明細件数 $n と合わない"
  [ "$(jq -r .processingDate "$out/import.json")" = "$DATE" ] \
    && ok "$name: 処理日 $DATE" || ng "$name: 処理日が $DATE でない"
  if cmp -s <(accrued_of "$accrued") "$out/accrued-after.txt"; then
    ok "$name: 取込後の各口座の未払利息 = ACCRUED.DAT の未払利息 ($n 口座)"
  else
    ng "$name: 取込後の未払利息が ACCRUED.DAT と違う"; diff <(accrued_of "$accrued") "$out/accrued-after.txt" | sed 's/^/    /'
  fi
  if cmp -s <(sed 's/ *$//' "$out/ACCOUNTS-after.DAT") "$accrued"; then
    ok "$name: 取込後に再取得した ACCOUNTS.DAT (行末空白を除く) = ACCRUED.DAT (トレーラの合計を含む)"
  else
    ng "$name: 取込後に再取得した ACCOUNTS.DAT が ACCRUED.DAT と違う"
    diff <(sed 's/ *$//' "$out/ACCOUNTS-after.DAT") "$accrued" | cat -A | sed 's/^/    /'
  fi
  log "GET /api/accounts → $(jq length "$out/accounts.json") 口座 (有効口座のみ。未払利息の項目はない)"
}

command -v cobc >/dev/null || fail "cobc (GnuCOBOL) がない"
for c in bc curl jq lsof mvn; do command -v $c >/dev/null || fail "$c がない"; done
[ -x "$COBOL_RUN" ] || fail "${COBOL_RUN#$ROOT/} がない"
rm -rf "$WORK"; mkdir -p "$WORK"

log "== 準備: Java 版 UBEOD001 をビルド (batch/java/ubeod001.sh --build-only) =="
"$ROOT/batch/java/ubeod001.sh" --build-only || fail "Java 版のビルドに失敗した"

log ""
log "== 手順 1: アプリ起動 (1 回目) と ACCOUNTS.DAT の取得 =="
start_app 1
api_get /api/batch/accounts-file "$WORK/ACCOUNTS.DAT"
DATE=$(head -c 9 "$WORK/ACCOUNTS.DAT" | cut -c2-)
log "GET /api/batch/accounts-file → ${WORK#$ROOT/}/ACCOUNTS.DAT ($(wc -c <"$WORK/ACCOUNTS.DAT") バイト、明細 $(grep -c '^D' "$WORK/ACCOUNTS.DAT") 件、処理日 $DATE)"
accrued_of "$WORK/ACCOUNTS.DAT" >"$WORK/accrued-before.txt"

log ""
log "== 手順 2: 同じ ACCOUNTS.DAT を COBOL 版と Java 版に流す =="
run_batch COBOL "$COBOL_RUN" "$WORK/cobol"
run_batch Java "$JAVA_RUN" "$WORK/java"
for f in ACCRUED.DAT UBEOD001.LOG stdout.txt rc.txt; do
  if [ ! -f "$WORK/cobol/$f" ] && [ ! -f "$WORK/java/$f" ]; then
    ng "$f: 新旧とも出力なし"
  elif cmp -s "$WORK/cobol/$f" "$WORK/java/$f"; then
    ok "$f: 新旧バイト一致 ($(wc -c <"$WORK/java/$f") バイト)"
  else
    ng "$f: 新旧で違う (左 COBOL / 右 Java)"
    diff <(cat -A "$WORK/cobol/$f" 2>/dev/null) <(cat -A "$WORK/java/$f" 2>/dev/null) | sed 's/^/    /'
  fi
done
BASE=$GOLDEN_DIR/baseline
if cmp -s <(tail -n +2 "$WORK/ACCOUNTS.DAT") <(tail -n +2 "$BASE/ACCOUNTS.DAT"); then
  ref=一致
  for f in ACCRUED.DAT UBEOD001.LOG stdout.txt; do
    cmp -s <(sed "s/20180315/$DATE/" "$BASE/$f") "$WORK/java/$f" || ref=不一致
  done
  log "参考: API の ACCOUNTS.DAT は golden/baseline と処理日 (ヘッダ) だけが違う。baseline の期待値の処理日を $DATE に差し替えたものと Java 版の出力: $ref"
else
  log "参考: API の ACCOUNTS.DAT は golden/baseline と明細も違う (baseline との比較はしない)"
fi

log ""
log "== 手順 3: Java 版の ACCRUED.DAT を取り込む (アプリ 1 回目) =="
import_leg Java "$WORK/java/ACCRUED.DAT" "$WORK/import-java"
stop_app

log ""
log "== 手順 4: アプリを再起動し、COBOL 版の ACCRUED.DAT を取り込む (アプリ 2 回目) =="
start_app 2
api_get /api/batch/accounts-file "$WORK/ACCOUNTS-2.DAT"
cmp -s "$WORK/ACCOUNTS.DAT" "$WORK/ACCOUNTS-2.DAT" \
  && ok "再起動後の ACCOUNTS.DAT = 手順 1 の ACCOUNTS.DAT (H2 が初期化されている)" \
  || fail "再起動後の ACCOUNTS.DAT が手順 1 と違う (H2 が初期化されていない、または処理日が変わった)"
import_leg COBOL "$WORK/cobol/ACCRUED.DAT" "$WORK/import-cobol"
stop_app

log ""
log "== 手順 5: 新旧の取込結果を比べる =="
J=$WORK/import-java C=$WORK/import-cobol
cmp -s <(jq -S . "$J/import.json") <(jq -S . "$C/import.json") \
  && ok "取込結果 (処理日・更新件数・スキップ件数) が新旧一致: $(jq -c . "$J/import.json")" \
  || ng "取込結果が新旧で違う: Java $(cat "$J/import.json") / COBOL $(cat "$C/import.json")"
cmp -s "$J/ACCOUNTS-after.DAT" "$C/ACCOUNTS-after.DAT" \
  && ok "取込後に再取得した ACCOUNTS.DAT が新旧バイト一致 (各口座の未払利息・トレーラ合計)" \
  || ng "取込後に再取得した ACCOUNTS.DAT が新旧で違う"
cmp -s "$J/accounts.sorted.json" "$C/accounts.sorted.json" \
  && ok "GET /api/accounts の応答が新旧一致" || ng "GET /api/accounts の応答が新旧で違う"

log ""
log "| 口座 | 取込前 | Java 版で取込後 | COBOL 版で取込後 | 増分 | 新旧 |"
log "|---|---:|---:|---:|---:|---|"
TOTAL=0
while read -r acct before java cobol; do
  d=$(echo "$java - $before" | bc | sed 's/^\(-*\)\./\10./'); TOTAL=$(echo "$TOTAL + $d" | bc | sed 's/^\(-*\)\./\10./')
  [ "$d" = 0 ] && d=0.00
  [ "$java" = "$cobol" ] && m=一致 || m=不一致
  log "| $acct | $before | $java | $cobol | $d | $m |"
done < <(join "$WORK/accrued-before.txt" "$J/accrued-after.txt" | join - "$C/accrued-after.txt")
LOG_TOTAL=$(sed -n 's/^TOUJITSU RISOKU GOUKEI (YEN) *//p' "$WORK/java/UBEOD001.LOG")
log ""
[ "$(echo "$TOTAL == $LOG_TOTAL" | bc)" = 1 ] \
  && ok "増分の合計 $TOTAL 円 = UBEOD001.LOG の当日利息合計 (TOUJITSU RISOKU GOUKEI) $LOG_TOTAL 円" \
  || ng "増分の合計 $TOTAL 円が UBEOD001.LOG の当日利息合計 $LOG_TOTAL 円と違う"

log ""
if [ "$NG" -eq 0 ]; then
  log "結果: 新旧一致 (ACCRUED.DAT・UBEOD001.LOG・stdout・RC、取込結果、各口座の未払利息)"
else
  log "結果: 不一致あり (上の NG を見る)"
fi
exit "$NG"
