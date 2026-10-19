# UBEOD001 Java 版 設計メモ（COBOL → Java 移植）

対象: `batch/cobol/UBEOD001.cbl` を Java 8 に移植した Java 版 UBEOD001
前提資料: [UBEOD001-spec.md](UBEOD001-spec.md)（以下「仕様書」）、[batch/golden/README.md](../../batch/golden/README.md)（以下「ゴールデン」）
合格基準: ゴールデン 24 ケースで、ACCRUED.DAT・UBEOD001.LOG・stdout・終了コードが COBOL と **バイト一致** すること

> 本書は設計だけを決める。コードは後続のステップ（s3.1 固定長レコード I/O と数値編集、s3.2 本体ロジック、s3.3 JUnit 組込み）で書く。
> **[実行確認]** は、この設計のために追加で COBOL を動かして確かめた項目。記録は §12。

---

## 0. 決定事項の一覧

| # | 項目 | 決定 | 本書 |
|---|---|---|---|
| 1 | 形（d-target・決定済み） | ファイル入出力の CLI バッチ。Java 8、既存の Maven プロジェクト（`usagi-bank`）の中に置く。Spring は起動しない | §1 |
| 2 | パッケージ・クラス | `jp.usagi.bank.batch.host` に `Ubeod001`（main）ほか 6 クラス | §2 |
| 3 | 起動方法 | 2 つのスクリプトに分ける。`batch/java/ubeod001.sh` = COBOL の実行ファイルに当たる起動コマンド（カレントで実行、終了コード = RC）。`batch/java/run.sh [ACCOUNTS.DAT]` = `batch/cobol/run.sh` と同じ使い方。`target/classes` + Maven が出すクラスパスで `java -cp` 起動（jar は作らない） | §3.1、§3.2 |
| 4 | ゴールデン比較 | `batch/java/ubeod001.sh` を `golden_main … -- batch/java/ubeod001.sh` にそのまま渡せる形にする。比較用に `batch/golden/run-java.sh` を置く | §3.3 |
| 5 | 数値 | 全項目 BigDecimal。格納はすべて `Pic.store`（小数部 `RoundingMode.DOWN`、整数部は 10^n の剰余） | §5 |
| 6 | 桁あふれ（d-overflow・決定済み） | COBOL に合わせて上位桁を切り捨て、RC=0。メッセージなし | §5.3 |
| 7 | 利息計算 | `InterestService.dailyInterest` を共有する。`WS-WORK` の 15 桁切捨てを差し込める 3 引数版を追加し、2 引数版はそれを呼ぶ | §6 |
| 8 | 文字コード | 変換しない。バイト列を ISO-8859-1 で 1 バイト = 1 文字として扱う（連携仕様上の文字コードは MS932 のまま） | §4.3 |
| 9 | MS932 の 2 バイト文字（仕様書 §8 #3、E23） | **エラーにしない**。COBOL と同じくバイト単位で桁を切り、そのまま出力する（バイト一致で再現できる） | §4.3 |
| 10 | 数値項目に数字以外（仕様書 §8 #2、E17 後半・E18） | **RC=12 で異常終了**。`UBEOD001E NOT NUMERIC <項目> RECORD=<9 桁>`。ACCRUED.DAT・LOG は作らない | §7.2 |
| 11 | 改行・末尾空白 | 入力: CR を全部除き LF で区切る。52 桁に切詰め/空白埋め。出力: 末尾の空白を落として LF | §4 |
| 12 | stdout と RC | DISPLAY 相当は stdout に LF 区切りで出す。RC は 0 / 8 / 12。stderr は異常時の調査情報だけ | §7 |
| 13 | 出力ファイルの OPEN・WRITE エラー（仕様書 §8 #4） | RC=12 で止める（COBOL は判定しない） | §7.2 |
| 14 | ファイル名・作業ディレクトリ（仕様書 §8 #5） | COBOL と同じくカレントディレクトリの固定ファイル名。引数では変えない | §3.1 |

---

## 1. 形

d-target（決定済み）のとおり、**ファイル入出力の CLI バッチ** にする。

- 外部インタフェースは COBOL と同じ: カレントの `ACCOUNTS.DAT` を読み、`ACCRUED.DAT`・`UBEOD001.LOG` を書き、DISPLAY 相当を stdout に出し、RC（プロセスの終了コード）を返す。
- 既存の Maven プロジェクトのソースツリー（`src/main/java`）に置く。別モジュールにはしない（`InterestService` を共有するため。§6）。
- Spring のコンテキストは起動しない。`public static void main` だけの素の Java。DB も使わない。クラスに `@Component` などを付けないので、オンライン側（`@SpringBootApplication` の部品スキャン）にも載らない。
- Java 8 の言語機能・API だけを使う（`java.nio.file`、`java.math`）。追加の依存ライブラリは入れない。

---

## 2. パッケージとクラス構成

パッケージ: `jp.usagi.bank.batch.host`（「ホスト側で動いていたバッチ」の置き場所。既存の `jp.usagi.bank.batch.EndOfDayJob` はオンライン側のスケジューラなので分ける）

| クラス | 公開範囲 | 役割 | COBOL の対応 | 作るステップ |
|---|---|---|---|---|
| `Ubeod001` | public | `main(String[])` と `static int run(Path dir, PrintStream out)`。処理の流れ（§3.4）、カウンタ、RC | PROCEDURE DIVISION 全体、`WS-COUNTERS`、`WS-CALC` | s3.2 |
| `Ubeod001Log` | パッケージ | LOG 7 行の組み立て（§5.4） | `3000-CLOSE`、`WS-LOG-LINE`、`WS-LOG-AMT-LINE` | s3.2 |
| `Field` | パッケージ | レコード定義。全項目を enum で持つ（COBOL 名・開始桁・桁数・`Pic`）（§4.4） | `WS-HDR` / `WS-DTL` / `WS-TRL` | s3.1 |
| `HostRecord` | パッケージ | 52 桁の固定長レコード。`kind()`、`text(Field)`、`number(Field)`（数字検査つき）、`put(Field, String)`、`toLine()` | `ACCT-REC`、`MOVE ACCT-REC TO WS-…` | s3.1 |
| `Pic` | パッケージ | 数値項目 `9(n)V9(m)` の定義と編集ユーティリティ。`parse`・`store`・`zoned`・`editZ`（§5） | PIC 句、MOVE/ADD/COMPUTE の格納規則、編集 PIC | s3.1 |
| `LineSequentialFile` | パッケージ | LINE SEQUENTIAL の読み書き（§4） | `ORGANIZATION IS LINE SEQUENTIAL` | s3.1 |
| `Ubeod001Abend` | パッケージ | RC=12 で止めるときの非検査例外（メッセージ 1 行を持つ） | `MOVE 12 TO RETURN-CODE` + `STOP RUN` | s3.2 |

`run(Path dir, PrintStream out)` は作業ディレクトリと stdout を引数で受け取り、RC を返す。`main` は `run(Paths.get(""), stdout)` を呼んで `System.exit(rc)` するだけにする。JUnit（s3.3）は `run` を一時ディレクトリで直接呼ぶ。

既存クラスの変更は `InterestService` の 1 か所だけ（§6）。`BatchFileService` は変更しない（オンライン側の入出力で、COBOL と挙動が違う箇所は仕様書 §7.2 のとおり。Java 版バッチはこれを使わない）。

---

## 3. 起動方法

### 3.1 `batch/java/ubeod001.sh` と `batch/java/run.sh`

起動用のスクリプトは 2 つに分ける。

| スクリプト | 役割 | COBOL の対応 | 呼ぶもの |
|---|---|---|---|
| `batch/java/ubeod001.sh` | Java 版 UBEOD001 そのものを起動するコマンド。カレントディレクトリで実行し、stdout はプログラムの出力だけ、終了コード = RC | `run-cobol.sh` が作る実行ファイル `work/ubeod001` | `golden_main`（`run-java.sh`）、`run.sh`、ジョブ管理 |
| `batch/java/run.sh` | 手で動かして確かめる用。`batch/cobol/run.sh` と同じ使い方 | `batch/cobol/run.sh` | 開発者・デモ |

#### `batch/java/ubeod001.sh`

```
使い方:
  batch/java/ubeod001.sh               カレントディレクトリで UBEOD001 を実行する
  batch/java/ubeod001.sh --build-only  ビルドだけして終了する (run-java.sh が最初に 1 回呼ぶ。§3.3)
入力 : ./ACCOUNTS.DAT              出力: ./ACCRUED.DAT, ./UBEOD001.LOG
stdout: UBEOD001 の DISPLAY メッセージだけ   終了コード: UBEOD001 の RC (0 / 8 / 12)
```

| 状況 | 動作 |
|---|---|
| 引数なし | カレントディレクトリで実行する。stdout にはプログラムのメッセージだけを出し、終了コード = RC。`golden_main [--update] [CASE...] -- batch/java/ubeod001.sh` がケースの作業ディレクトリをカレントにして呼ぶので、そのまま比較できる。スクリプト自身の場所は `$(cd "$(dirname "$0")" && pwd)` で求め、カレントがどこでもリポジトリのルートを見つけられるようにする |
| `--build-only` | 下の「ビルド」を必ず実行して終了する（class の有無に関係なく `mvn compile` を走らせ、古い class で比較しないようにする）。成功なら 0 |
| それ以外の引数 | stderr に使い方を出して終了コード 2 |
| ビルド | 引数なしのとき、`target/classes/jp/usagi/bank/batch/host/Ubeod001.class` か `target/ubeod001.classpath` がなければ、リポジトリのルートで `mvn -B -q -DskipTests compile dependency:build-classpath -Dmdep.includeScope=runtime -Dmdep.outputFile=target/ubeod001.classpath` を実行する。Maven の出力はすべて **stderr** に回す（stdout はゴールデン比較の対象のため） |
| JDK | `JAVA_HOME` があれば `$JAVA_HOME/bin/java`、なければ `java`。`java -version` が `1.8` でなければ stderr にメッセージを出して終了コード 2 |
| 起動失敗 | ビルド失敗・JDK 不一致などは終了コード 2（`run-cobol.sh` の「2 = コンパイル失敗」と同じ）。RC の 0 / 8 / 12 とは重ならない |

#### `batch/java/run.sh`

```
使い方: batch/java/run.sh [ACCOUNTS.DAT へのパス]      (既定: batch/golden/baseline/ACCOUNTS.DAT)
出力  : batch/java/work/ACCRUED.DAT, batch/java/work/UBEOD001.LOG
stdout: プログラムの出力 + "RC=<RC>" + "GOLDEN OK: …" / "GOLDEN MISMATCH: …"
```

```sh
#!/bin/sh
# set -e は使わない (RC=8・12 でも RC= と比較まで出す。仕様書 E25)
JAVA_DIR=$(cd "$(dirname "$0")" && pwd)
INPUT=${1:-$JAVA_DIR/../golden/baseline/ACCOUNTS.DAT}
mkdir -p "$JAVA_DIR/work" && cp "$INPUT" "$JAVA_DIR/work/ACCOUNTS.DAT" || exit 2   # INPUT は呼び出し元のカレント基準
cd "$JAVA_DIR/work" || exit 2
rm -f ACCRUED.DAT UBEOD001.LOG
"$JAVA_DIR/ubeod001.sh"; RC=$?
[ "$RC" -eq 2 ] && exit 2                       # 起動失敗
echo "RC=$RC"
BASE=$JAVA_DIR/../golden/baseline
if cmp -s ACCRUED.DAT "$BASE/ACCRUED.DAT"; then
  echo "GOLDEN OK: ACCRUED.DAT matches golden/baseline/ACCRUED.DAT"
else
  echo "GOLDEN MISMATCH: ACCRUED.DAT differs from golden/baseline/ACCRUED.DAT"; diff ACCRUED.DAT "$BASE/ACCRUED.DAT"; exit 1
fi
exit $RC
```

- 既定の入力は `batch/golden/baseline/ACCOUNTS.DAT`（`batch/cobol/data/ACCOUNTS.DAT` と同じ内容）。p5 で `batch/cobol/` を消しても動くように、`batch/cobol/` を参照しない。比較相手も `batch/golden/baseline/ACCRUED.DAT`（`batch/cobol/expected/ACCRUED.DAT` と同じ内容）。
- `batch/cobol/run.sh` と同じく、入力が baseline 以外のときも baseline の期待値と比べる（MISMATCH になるのは正しい動作。任意の入力の結果は `work/` を見る）。
- `batch/cobol/run.sh` との違い（意図したもの）:
  - `set -e` を使わない。COBOL 版は RC=8・12 のとき `RC=` の行も比較も出さずに終了する（仕様書 E25）。Java 版は RC=8・12 でも `RC=…` を出し、比較まで進む。
  - 引数の相対パスは **呼び出し元のカレントディレクトリ基準**。COBOL 版は先に `cd` するので `batch/cobol/` 基準になる。
  - 前回の `work/ACCRUED.DAT`・`UBEOD001.LOG` を消してから実行する（RC=12 のとき、前回の出力を今回の結果と取り違えない）。
- 終了コード: 一致 → プログラムの RC（0 / 8 / 12）、不一致 → 1、起動失敗 → 2（COBOL 版と同じ）。

`.gitignore` に `batch/java/work/` を追加する。

### 3.2 jar ではなくクラスパス起動にする理由

**決定: `target/classes` + Maven が解決した依存 jar のクラスパスで `java -cp … jp.usagi.bank.batch.host.Ubeod001` を起動する。** jar は作らない。

- このプロジェクトの成果物は Spring Boot で repackage した **war**（`usagi-bank.war`）で、依存 jar は `WEB-INF/lib/` に入れ子になっている。素の `java -cp usagi-bank.war` では読めない。
- バッチ用の jar を別に作るには、`maven-jar-plugin` の追加実行や shade / assembly などのビルド設定が要る。共有する `InterestService` が slf4j を使う（クラス初期化で `LoggerFactory` を呼ぶ）ので、依存込みの jar にする必要もある。1 クラスのバッチに対して重い。
- `batch/cobol/run.sh` も実行のたびに `cobc` でコンパイルしている。`mvn compile` も同じく「ソースから作って動かす」形で、増分コンパイルなので 2 回目以降は数秒。
- **[実行確認]** この VM（JDK 8）で、`target/classes` だけのクラスパスでは `InterestService.dailyInterest` の呼び出しが `NoClassDefFoundError: org/slf4j/LoggerFactory` で失敗し、`dependency:build-classpath`（runtime スコープ）の結果を足すと動いた。`mvn -q -DskipTests compile dependency:build-classpath` は 4 秒、`java` の起動から終了までは 0.13 秒。終了コードは `System.exit` の値がそのまま返る。
- 本番のジョブ管理に載せる段階で配布物が必要になったら、`maven-jar-plugin` の classifier 付き jar + `dependency:copy-dependencies` に切り替える。起動するクラスと外部インタフェースは変わらない（今回の範囲外）。

### 3.3 ゴールデン比較 `batch/golden/run-java.sh`

`run-cobol.sh` と同じ形で `batch/golden/run-java.sh` を置く（作るのは s2.2 新旧比較ハーネス）。

```bash
#!/bin/bash
# UBEOD001 ゴールデンテスト (Java 版, JDK 8 と Maven が必要)
#   使い方: ./run-java.sh [--update] [CASE...]      (終了コードは run-cobol.sh と同じ)
set -u
GOLDEN_DIR=$(cd "$(dirname "$0")" && pwd)
GOLDEN_WORK=${GOLDEN_WORK:-$GOLDEN_DIR/work-java}
. "$GOLDEN_DIR/golden-lib.sh"
"$GOLDEN_DIR/../java/ubeod001.sh" --build-only || exit 2     # 1 回だけビルド (ケースごとにはビルドしない)
golden_main "$@" -- "$GOLDEN_DIR/../java/ubeod001.sh"
```

- 先に `ubeod001.sh --build-only` で 1 回だけビルドし、各ケースでは `ubeod001.sh` を引数なしで呼ぶ（§3.1）。
- 作業ディレクトリは COBOL と分けて `batch/golden/work-java/`（`.gitignore` に追加）。COBOL と Java を続けて実行しても互いの結果を消さない。
- `--update` は Java 版では使わない（期待値は COBOL の結果だけで作る、というゴールデンの方針のため）。`golden_main` の機能としては動くが、README に「Java 版で `--update` しない」と書く。
- `batch/golden/README.md` の「Java 版での使い方（予定）」は `java -jar` の例になっているので、s2.2 で `run-java.sh` に書き換える。

### 3.4 処理の流れ

COBOL の段落と 1 対 1 に対応させる（メソッド名に段落番号を残す）。

```
run(dir, out)
  open1000()     ACCOUNTS.DAT を開く。開けなければ OPEN ERROR → RC=12（出力ファイルは作らない）
                 ACCRUED.DAT.tmp / UBEOD001.LOG.tmp を作る（作れなければ RC=12）
  レコードを 1 件ずつ読み、区分（1 バイト目）で分岐            ← 2000-PROCESS / 2100-READ
    'H' → header2200()   'D' → detail2300()   'T' → trailer2400()
    それ以外 → "UBEOD001E UNKNOWN RECORD KIND: " + 区分、RC=8（出力しない・数えない）
  close3000()    LOG 7 行を書き、全ファイルを閉じ、.tmp を本来の名前に置き換え、完了メッセージ
  return RC
途中で Ubeod001Abend → メッセージを出し、.tmp を消し、RC=12（完了メッセージなし）
```

- 出力は一時ファイル（`ACCRUED.DAT.tmp`、`UBEOD001.LOG.tmp`）に書き、正常に閉じたあと `Files.move(…, REPLACE_EXISTING, ATOMIC_MOVE)` で置き換える。RC=12 で止めたときに **途中までの ACCRUED.DAT を残さない** ため（後続の `POST /api/batch/accrued-file` に半端なファイルが渡らないようにする）。RC=12 のとき既存の ACCRUED.DAT・LOG には触らない（COBOL の OPEN エラー時と同じ）。
- 入力の並び順は検査しない（仕様書 §1.1）。ヘッダ・トレーラがなくても、2 回目のヘッダやトレーラの後のレコードがあっても、COBOL と同じく出現順に処理する。
- カウンタはヘッダで 0 に戻さない（仕様書 §4.3、E16）。

---

## 4. 入出力（LINE SEQUENTIAL・文字コード）

### 4.1 入力の読み方（`LineSequentialFile.read`）

| 入力 | 扱い | 根拠 |
|---|---|---|
| 区切り | LF（0x0A）で区切る。最終行に LF がなくても 1 レコード。ファイルが LF で終わるとき、その後ろに空のレコードは作らない | 仕様書 §3.2、E20 |
| CR（0x0D） | **行のどこにあっても全部取り除く**（CRLF の CR だけでなく行の途中の CR も） | E19、**[実行確認 X01・X02]** |
| 52 バイト未満 | 右を空白（0x20）で埋めて 52 バイトにする | 仕様書 §3.2 |
| 52 バイト超 | 53 バイト目以降を捨てる（エラーにしない） | E17 前半 |
| 空行 | 52 バイトの空白 → 区分が空白なので未知の区分（RC=8） | E11 |
| NUL（0x00） | **RC=12**（§7.2）。GnuCOBOL は NUL の前後で桁をずらして読み、ほかの制御文字と扱いが違う | **[実行確認 X03]** |
| その他の制御文字（0x01–0x1F の CR・LF 以外、0x7F）と 0x80–0xFF | 普通の 1 バイトとしてそのまま扱う（TAB の展開などはしない） | **[実行確認 X04]** |

ファイル全体を 1 回読むのではなく、`BufferedInputStream` から 1 バイトずつ読んでレコードを組み立てる（入力の大きさに依存しない）。

### 4.2 出力の書き方（`LineSequentialFile.write`）

- ACCRUED.DAT は 52 バイト、LOG は 80 バイトのレコードを組み立て、**末尾の空白（0x20）だけを取り除いて** LF（0x0A）を付ける。CR は出さない。途中の空白は残す（仕様書 §3.1）。
- 末尾空白を落とすのは 0x20 だけ。TAB などほかのバイトは残す。
- 結果: ヘッダは `H20180315` + LF、トレーラは 44 バイト + LF（FILLER が空白のとき）、D は常に 52 バイト + LF、LOG は 31 / 47 / 47 / 47 / 47 / 48 / 48 バイト（仕様書 §2.2、§3.1）。

### 4.3 文字コード（MS932）と 2 バイト文字

**決定: 文字コードの変換をしない。** 入力のバイト列を `StandardCharsets.ISO_8859_1` で文字列にし（1 バイト = 1 文字、0x00–0xFF がそのまま U+0000–U+00FF になる）、出力も ISO-8859-1 でバイトに戻す。

- 連携仕様上の文字コードは MS932（`BatchFileService.HOST_CHARSET`）のまま変えない。ただし Java 版バッチは COBOL と同じく **バイト単位** で桁を数える。MS932 で文字列にすると 2 バイト文字が 1 文字に縮んで `substring` の位置がずれる（仕様書 §3.3、付録 B）ため、MS932 でデコードしない。
- **MS932 の 2 バイト文字（仕様書 §8 #3、E23）はエラーにしない。** COBOL はバイト単位で処理して RC=0 で正しく桁を切り、2 バイト文字はそのまま出力に残る（E23: 入力・出力とも 52 バイト）。ISO-8859-1 でのバイト単位処理なら同じバイト列を出せるので、COBOL と一致させられる。業務データは英数字だけなので通常は起きないが、起きても COBOL と同じ結果になる。
  - 理由 1: 再現できる挙動をわざわざエラーにすると、COBOL で通っていたファイルが Java 版で止まる（置換の互換性が下がる）。
  - 理由 2: MS932 の 2 バイト目は 0x40–0x7E・0x80–0xFC で、空白（0x20）・CR・LF・数字（0x30–0x39）とは重ならない。末尾空白の除去、改行の区切り、数字検査（§7.2）が 2 バイト文字の途中で誤動作することはない。
- 2 バイト文字が桁位置をまたいで切れる（例: 口座番号 7 バイトの最後が 1 バイト目）場合も、COBOL と同じくバイトのまま切る。文字として正しいかは見ない。
- ゴールデンには E23 相当のケースがない（ゴールデン「入れていないもの」）。この決定で Java 版も再現できる挙動になったので、**COBOL で期待値を作るケース `ms932-dbcs` を追加する**（入力は仕様書 付録 A の E23 と同じ）。追加は s2.2 で行う。
- stdout のメッセージに入力のバイトが入る場合（未知の区分の 1 バイト）も、COBOL と同じく **そのバイトのまま** 出す。stdout は `new PrintStream(new FileOutputStream(FileDescriptor.out), true, "ISO-8859-1")` で包む（`System.out` の既定の文字コードに依存しない）。

### 4.4 レコード定義（`Field`）

桁位置は仕様書 §2.1 のとおり。`Field` の各定数に COBOL 名・開始バイト（0 始まり）・バイト数・`Pic`（数値項目だけ）を持たせる。

| `Field` | COBOL 名 | substring | `Pic` |
|---|---|---|---|
| `KIND` | `ACCT-KIND` | 0–1 | — |
| `HDR_DATE` | `WS-HDR-DATE` | 1–9 | `Pic.of(8, 0)` |
| `DTL_BRANCH` / `DTL_ACCT_NO` / `DTL_TYPE` / `DTL_STATUS` | `WS-DTL-…` | 1–4 / 4–11 / 11–12 / 12–13 | — |
| `DTL_BALANCE` | `WS-DTL-BALANCE` | 13–28 | `Pic.of(15, 0)` |
| `DTL_RATE` | `WS-DTL-RATE` | 28–35 | `Pic.of(3, 4)` |
| `DTL_ACCRUED` | `WS-DTL-ACCRUED` | 35–52 | `Pic.of(15, 2)` |
| `TRL_COUNT` | `WS-TRL-COUNT` | 1–10 | `Pic.of(9, 0)` |
| `TRL_BALANCE` | `WS-TRL-BALANCE` | 10–27 | `Pic.of(17, 0)` |
| `TRL_ACCRUED` | `WS-TRL-ACCRUED` | 27–44 | `Pic.of(15, 2)` |

レコードの出力は COBOL と同じく「入力の 52 バイトの一部を書き換える」形にする（`HostRecord.put`）。

- H: 入力の 52 バイトをそのまま出す（FILLER も引き継ぐ。E21）。
- D: 利息対象なら `DTL_ACCRUED` だけ書き換える。対象外は入力のバイトのまま（仕様書 §2.1）。
- T: `KIND`='T'、`TRL_COUNT`・`TRL_BALANCE`・`TRL_ACCRUED` を再集計値で書き換え、45–52 バイト目の FILLER は入力のまま（E21）。

---

## 5. 数値（BigDecimal と PIC の対応）

### 5.1 `Pic` の操作

`Pic.of(n, m)` = `9(n)V9(m)`（整数 n 桁・小数 m 桁、符号なし）。値はすべて `java.math.BigDecimal`（件数も含む。`long`・`double` は使わない）。

| メソッド | 内容 | COBOL の対応 |
|---|---|---|
| `parse(String)` | n+m バイトが全部 `'0'`–`'9'` なら `new BigDecimal(new BigInteger(s), m)`。そうでなければ数字以外（§7.2） | 数値項目の参照 |
| `store(BigDecimal)` | `v.setScale(m, RoundingMode.DOWN).remainder(10^n)` | MOVE / ADD / COMPUTE（`ROUNDED` も `ON SIZE ERROR` もない）の格納 |
| `zoned(BigDecimal)` | `store` した値の unscaled 値を n+m 桁にゼロ埋め | 表示形式の数値項目への MOVE（レコードへの書き出し） |
| `Pic.editZ(BigDecimal, int z, int scale)` | 編集 PIC `Z(z)9` / `Z(z)9.99`（§5.4） | `WS-LOG-VALUE` / `WS-LOG-AMT-VALUE` |

- 値はすべて 0 以上なので `RoundingMode.DOWN`（0 方向への切捨て）= COBOL の切捨て（`ROUNDED` なし・`MODE TRUNCATION`）= `BigDecimal.ROUND_DOWN`。
- `remainder(10^n)` は 0 以上の値に対して「上位桁を落とした下 n 桁」を返す（§5.3）。

### 5.2 PIC 対応表

| COBOL 項目 | PIC | `Pic` | 使い方 | 切捨て・桁あふれ |
|---|---|---|---|---|
| `WS-HDR-DATE` | 9(08) | `of(8, 0)` | `parse`（ヘッダを読んだとき）→ LOG 2 行目。ヘッダなしなら 0 | — |
| `WS-DTL-BALANCE` | 9(15) | `of(15, 0)` | `parse` | — |
| `WS-DTL-RATE` | 9(03)V9(04) | `of(3, 4)` | `parse`（利息対象のときだけ。§7.2） | — |
| `WS-DTL-ACCRUED` | 9(15)V9(02) | `of(15, 2)` | `parse` → `store(accrued + daily)` → `zoned` | 上位桁を切捨て（E06、`accrued-overflow`） |
| `WS-WORK` | 9(15)V9(10) | `of(15, 10)` | `store(残高 × 利率 ÷ 100)`（§6） | 小数 11 桁目以降を切捨て（実際には起きない）、上位桁を切捨て（E03・E04、`max-digits`） |
| `WS-DAYS-IN-YEAR` | 9(03) VALUE 365 | — | `InterestService.DAYS_IN_YEAR`（365） | — |
| `WS-DAILY-INT` | 9(15)V9(02) | `of(15, 2)` | `store(WORK ÷ 365)`。÷ は小数 2 桁で `DOWN` | 小数 3 桁目以降を切捨て。上位桁はあふれない（WORK が 15 桁で切られるため） |
| `WS-READ-COUNT` / `WS-ACCRUED-COUNT` / `WS-SKIPPED-COUNT` | 9(09) | `of(9, 0)` | `store(count + 1)` | 10 億件で 0 に戻る（COBOL と同じ。実際には起きない） |
| `WS-TOTAL-BALANCE` | 9(17) | `of(17, 0)` | `store(total + balance)` | 上位桁を切捨て（E07、`total-balance-overflow`） |
| `WS-TOTAL-ACCRUED` | 9(15)V9(02) | `of(15, 2)` | `store(total + 加算後の accrued)` | 上位桁を切捨て（E08、`total-accrued-overflow`） |
| `WS-TOTAL-DAILY` | 9(15)V9(02) | `of(15, 2)` | `store(total + daily)` | 上位桁を切捨て |
| `WS-TRL-COUNT`（入力） | 9(09) | `of(9, 0)` | 9 バイトが全部空白なら不一致（§7.2）。それ以外は `parse` → `WS-READ-COUNT` と `compareTo` | — |
| `WS-TRL-COUNT` / `-BALANCE` / `-ACCRUED`（出力） | 9(09) / 9(17) / 9(15)V9(02) | `of(9,0)` / `of(17,0)` / `of(15,2)` | 集計値を `zoned` | 集計値は既に各 PIC に収まっている |
| `WS-LOG-VALUE` | Z(16)9 | `editZ(v, 16, 0)` | LOG 2–5 行目 | あふれない（仕様書 §4.4） |
| `WS-LOG-AMT-VALUE` | Z(14)9.99 | `editZ(v, 14, 2)` | LOG 6・7 行目 | あふれない（同上） |

### 5.3 桁あふれ（d-overflow）

**決定（マネージャー決定 d-overflow）: COBOL に合わせて上位桁を切り捨て、RC=0。メッセージも出さない。**

- 実装は「格納するたびに必ず `Pic.store` を通す」だけにする。演算の途中は BigDecimal の任意精度で計算し、受け側の項目に入れるときに切る（GnuCOBOL が右辺を任意精度で計算してから格納するのと同じ。仕様書 §4.2）。
- `BatchFileService.numeric` の `UB-9004`（桁あふれ例外）は使わない（仕様書 §7.2-2）。
- **[実行確認 X05]** この規則（`setScale(m, DOWN).remainder(10^n)`）で、仕様書の E03–E08 とゴールデン基準値の数値がすべて一致することを JShell で確かめた（§12）。

### 5.4 LOG の編集（`Ubeod001Log`）

仕様書 §2.2 のとおり、80 バイトで組み立てて末尾空白を落とす。

```java
String countLine = String.format("%-30s%17s", label, Pic.editZ(value, 16, 0));   // + 33 空白 → 末尾空白は落ちる
String amtLine   = String.format("%-30s%18s", label, Pic.editZ(amount, 14, 2));  // + 32 空白
// editZ(v, z, 0) = v.toBigInteger().toString()          を z+1 桁に右寄せ (0 は "0")
// editZ(v, z, 2) = v.setScale(2, DOWN).toPlainString() を z+4 桁に右寄せ (0 は "0.00")
```

1 行目は `UBEOD001 NICHIJI RISOKU SEKISUU`（COBOL リテラルの末尾空白 2 個は出力で落ちる）。

---

## 6. 利息計算 — `InterestService.dailyInterest` を共有する

**決定: 共有する。** 計算式（÷100 を小数 10 桁で切捨て → ÷365 を小数 2 桁で切捨て）をオンラインとバッチの 2 か所に分けない。

ただし今の `dailyInterest(balance, rate)` には `WS-WORK` の整数 15 桁の制限がなく、桁あふれする入力（E03・E04、ゴールデン `max-digits`）で COBOL と値が違う（仕様書 §7.2-2、付録 B）。そこで **中間値 WORK の格納規則を差し込める 3 引数版** を追加し、既存の 2 引数版はそれを恒等関数で呼ぶ。

```java
/** 1日分の利息. 銭未満切捨. */
public static BigDecimal dailyInterest(BigDecimal balance, BigDecimal annualRatePercent) {
    return dailyInterest(balance, annualRatePercent, UnaryOperator.identity());
}

/** 1日分の利息. work は 残高×年利÷100 (小数10桁切捨) を受け取る中間項目の格納規則. */
public static BigDecimal dailyInterest(BigDecimal balance, BigDecimal annualRatePercent,
        UnaryOperator<BigDecimal> work) {
    if (balance.signum() <= 0) {
        return BigDecimal.ZERO.setScale(2);
    }
    return work.apply(balance.multiply(annualRatePercent).divide(HUNDRED, 10, BigDecimal.ROUND_DOWN))
            .divide(DAYS_IN_YEAR, 2, BigDecimal.ROUND_DOWN);
}
```

バッチ側の呼び出し:

```java
BigDecimal daily = Pic.of(15, 2).store(InterestService.dailyInterest(balance, rate, Pic.of(15, 10)::store));
```

- オンライン側（`accrueDaily`、`CobolParityTest`、`InterestServiceTest`）の結果は変わらない（2 引数版の計算は同じ）。既存 39 テストはそのまま通ること。
- `balance.signum() <= 0` の分岐: COBOL の残高は符号なしで、0 なら計算結果も 0.00 なので一致する（仕様書 §7.2-11、ゴールデン `balance-zero`）。
- `InterestService` を読み込むと slf4j の `LoggerFactory` が初期化されるので、起動時のクラスパスに依存 jar が要る（§3.2）。Spring のコンテキストは起動しない（`@Service` などのアノテーションはクラスの読み込みに影響しない）。
- あわせて `InterestService` の Javadoc の古い参照 `cobol/INTCALC.cbl` を `batch/cobol/UBEOD001.cbl` に直す（仕様書 §7.2-12）。
- 利息対象の判定は `InterestService.bearsInterest(Account)` を **使わない**。enum への変換が要り、全銀コードにない種目（`3`、空白）でエラーになるため（仕様書 §7.2-5、ゴールデン `type-status-codes`）。バッチ側は COBOL と同じくバイトで比べる: `status == 'A' && type != '2'`。

---

## 7. stdout・RC・エラー

### 7.1 終了コードと DISPLAY（COBOL と同じもの）

メッセージは stdout に 1 行ずつ、改行は LF（`println` は使わず `"\n"` を書く）。数値は 9 桁ゼロ埋め（`Pic.of(9,0).zoned`）。文言は仕様書 §6.2 と 1 文字も変えない。

| RC | 条件 | メッセージ | その後 |
|---|---|---|---|
| 0 | 正常 | `UBEOD001I COMPLETED. READ=… ACCRUED=… SKIPPED=…` | — |
| 8 | 未知の区分（空行を含む） | `UBEOD001E UNKNOWN RECORD KIND: <区分 1 バイト>` | 続行。その行は出力せず数えない |
| 8 | トレーラ件数不一致 | `UBEOD001E TRAILER COUNT MISMATCH IN=<入力の 9 バイト> READ=<9 桁>` | 続行。トレーラは再集計値で出す |
| 12 | ACCOUNTS.DAT の OPEN エラー | `UBEOD001E ACCOUNTS.DAT OPEN ERROR <2 桁>` | 直ちに終了。出力ファイルは作らない。完了メッセージなし |

- RC は「一度 8 になったら 0 に戻らない」。12 は 8 より優先する。
- OPEN エラーの 2 桁: ファイルがない（`NoSuchFileException`）→ `35`、読めない（`AccessDeniedException`）→ `37`（COBOL と同じ。**[実行確認 X06]**）、ディレクトリなどその他 → `30`。
- 完了メッセージは RC=8 でも出す。RC=12 では出さない。
- stderr: 通常は何も出さない（仕様書 E24）。予期しない例外のときだけスタックトレースを出す（§7.2 の最後の行）。

### 7.2 Java 版で追加するエラー（COBOL で再現できない入力・状況）

マネージャー指示のとおり、COBOL で結果を再現できない入力だけをエラーにする。**どれも RC=12**（異常終了）。

| 状況 | メッセージ | 理由 |
|---|---|---|
| 数値項目に数字以外（仕様書 E17 後半・E18、§8 #2） | `UBEOD001E NOT NUMERIC <項目名> RECORD=<レコード番号 9 桁>` 例: `UBEOD001E NOT NUMERIC DTL-BALANCE RECORD=000000002` | COBOL は検査せず、GnuCOBOL の内部実装に依存する意味のない値を RC=0 で出力する（仕様書 §4.5）。再現できず、業務的にも抽出ファイルの破損なので、後続の取込（`POST /api/batch/accrued-file`）に渡さず止める |
| 入力に NUL（0x00） | `UBEOD001E NUL BYTE RECORD=<9 桁>` | GnuCOBOL は NUL の前後で桁をずらして読み（**[実行確認 X03]**）、再現の対象にしない |
| ACCOUNTS.DAT がディレクトリなど | `UBEOD001E ACCOUNTS.DAT OPEN ERROR 30` | COBOL は空ファイル扱いで RC=0 になる（**[実行確認 X06]**）。ACCRUED.DAT が空のまま正常終了すると取込が何もしないまま成功するので止める |
| ACCRUED.DAT・UBEOD001.LOG の作成・書込み・置き換えに失敗（仕様書 §8 #4） | `UBEOD001E ACCRUED.DAT WRITE ERROR 30` / `UBEOD001E UBEOD001.LOG WRITE ERROR 30` | COBOL は判定せず RC=0 で、出力が作られないまま正常終了する（**[実行確認 X07]**） |
| 予期しない例外（バグ） | `UBEOD001E ABEND <例外クラス名>`（stdout）+ スタックトレース（stderr） | 終了コード 1 などの JVM 既定値ではなく 12 にそろえる |

RC=12 で止めたとき:

- それまでに出したメッセージ（`UNKNOWN RECORD KIND` など）はそのまま残り、最後にエラーの 1 行を出す。完了メッセージは出さない。
- ACCRUED.DAT・UBEOD001.LOG は作らない（一時ファイルを消す。§3.4）。ゴールデンの `no-input`（RC=12）と同じ「出力なし」の形になる。

**数字検査の範囲と順序** — COBOL が数値として使う項目を、使うときにだけ検査する。COBOL が数値として使わない項目はバイトのまま通すので、COBOL と同じ結果になる。

| 項目名（メッセージ） | 検査するとき | 検査しないとき |
|---|---|---|
| `HDR-DATE` | H を読んだとき | — |
| `DTL-BALANCE` | すべての D（残高合計に足すため） | — |
| `DTL-RATE` | 利息対象の D（計算に使うため） | 対象外の D: 利率はバイトのまま出力される（**[実行確認 X08]**） |
| `DTL-ACCRUED` | すべての D（対象外も未払利息合計に足すため） | — |
| `TRL-COUNT` | T を読んだとき | 9 バイトが全部空白のとき: 件数不一致として扱う（下の説明） |

- 1 つの D で複数の項目が数字以外のときは、COBOL が使う順（`DTL-BALANCE` → `DTL-RATE` → `DTL-ACCRUED`）で最初の 1 つを出す。
- レコード番号 = 入力の何レコード目か（1 始まり、H・D・T・未知の区分・空行をすべて数える）。
- 数字は `'0'`–`'9'`（0x30–0x39）だけ。空白・符号・小数点・全角数字は数字以外。
- 仕様書の例との対応: E17 後半（29 バイトの D、状態 A・種目 1）→ 15 バイトの残高は数字、利率に空白が入るので `NOT NUMERIC DTL-RATE RECORD=000000003`。E18（残高が空白）→ `NOT NUMERIC DTL-BALANCE RECORD=000000002`。
- **トレーラの件数（`TRL-COUNT`）が 9 バイトとも空白** のときは COBOL と同じにする: `UBEOD001E TRAILER COUNT MISMATCH IN=<空白 9 バイト> READ=<9 桁>` を出し、RC=8 で続行し、トレーラは再集計値で出力する。COBOL で再現できる（**[実行確認 X09]**）ので RC=12 にはしない。`T` だけのトレーラ（2 バイト目以降が空白）もこれに当たる。ゴールデンに `trailer-count-blank` を追加する（s2.2）。
- `TRL-COUNT` に空白以外の数字以外が入っている（空白と数字が混ざっている場合を含む）ときだけ `NOT NUMERIC TRL-COUNT` で RC=12。比較の結果が GnuCOBOL の内部実装に依存するため。
- `NOT NUMERIC` と NUL のケースはゴールデン（COBOL が正）には入れない。s3.3 の JUnit で Java 版の期待値として確かめる（§10）。

---

## 8. 仕様書 §8「移植時に決めること」への回答

| # | 論点 | 決定 | 本書 |
|---|---|---|---|
| 1 | 桁あふれ | COBOL に合わせて上位桁を切捨て、RC=0（d-overflow） | §5.3 |
| 2 | 数値項目に数字以外 | RC=12、`UBEOD001E NOT NUMERIC <項目> RECORD=<9 桁>`、出力なし。COBOL が数値として使う項目だけを検査 | §7.2 |
| 3 | ASCII 以外のバイト | バイト単位で処理する（エラーにしない）。MS932 の 2 バイト文字も COBOL と同じ出力。ゴールデンに `ms932-dbcs` を追加 | §4.3 |
| 4 | 出力ファイルの OPEN・WRITE エラー | RC=12、`UBEOD001E <ファイル名> WRITE ERROR 30` | §7.2 |
| 5 | ファイル名・作業ディレクトリ | COBOL と同じくカレントの固定ファイル名。プログラムの引数では変えない（`run.sh` の引数は `batch/cobol/run.sh` と同じ「入力ファイルを work/ にコピー」。`ubeod001.sh` は引数を取らない） | §3.1 |

---

## 9. 仕様書の各項目と設計の対応

| 仕様書 | 内容 | 設計での扱い | 確かめ方（ゴールデン / JUnit） |
|---|---|---|---|
| §1 概要 | 入力・出力・stdout・RC、固定ファイル名、`run.sh` | §1、§3.1（`ubeod001.sh`・`run.sh`） | 全ケース、`run.sh` で baseline |
| §1.1 処理の流れ | OPEN → READ ループ → CLOSE、並び順を検査しない、ヘッダ・トレーラなし、空ファイル | §3.4 | `no-header`・`no-trailer`・`records-after-trailer`・`empty-file`・`header-trailer-only` |
| §2.1 レイアウト（H/D/T） | 桁位置、PIC | §4.4 `Field`、§5.2 | `baseline` |
| §2.1 FILLER の引き継ぎ | H は入力のまま、T の 45–52 桁は入力のまま | §4.4 | `filler-passthrough` |
| §2.2 LOG | 7 行、ラベル 30 桁、`Z(16)9`・`Z(14)9.99`、末尾空白なし | §5.4 `Ubeod001Log` | 全ケースの UBEOD001.LOG |
| §3.1 出力の末尾空白 | 末尾の空白を落として LF | §4.2 | 全ケース（特に H・T・LOG） |
| §3.2 入力 | 52 桁未満は空白埋め、超は切捨て、CRLF、最終行改行なし、空行 | §4.1 | `long-record`・`crlf-input`・`no-final-newline`・`blank-line` |
| §3.3 改行コードと文字コード | 出力は LF のみ、バイト単位、MS932 の 2 バイト文字 | §4.2、§4.3 | 全ケース、`ms932-dbcs`（追加） |
| §4.1 利息対象の判定 | 状態 A かつ種目 2 以外（バイト比較） | §6 の最後 | `type-status-codes`・`status-fdc`・`current-only` |
| §4.2 日次利息 | WORK = 残高 × 利率 ÷ 100（9(15)V9(10)）、÷365 を小数 2 桁で切捨て | §6、§5.2 | `baseline`・`rate-zero`・`balance-zero`・`max-digits` |
| §4.3 集計 | カウンタ・合計、ヘッダで戻さない、加算後の未払利息を合計 | §3.4、§5.2 | `records-after-trailer`・`baseline` |
| §4.4 桁あふれ | 上位桁の切捨て、RC=0 | §5.3 | `max-digits`・`accrued-near-overflow`・`accrued-overflow`・`total-balance-overflow`・`total-accrued-overflow` |
| §4.5 数値項目に数字以外 | 再現の対象にしない | §7.2（RC=12） | JUnit（Java 版の期待値） |
| §5 トレーラ処理 | 件数だけ照合、再集計値で出力、不一致は RC=8 で続行 | §4.4、§7.1、§7.2（件数が空白） | `trailer-count-mismatch`、`trailer-count-blank`（追加） |
| §6.1 終了コード | 0 / 8 / 12、8 は戻らない、12 は出力なし | §7.1 | `unknown-record-kind`・`trailer-count-mismatch`・`no-input` |
| §6.1 `run.sh` の `set -e` | RC≠0 で比較まで進まない | §3.1（Java 版 `run.sh` は `set -e` を使わず、RC=8・12 でも `RC=` と比較を出す） | `run.sh` に RC=8 の入力 |
| §6.2 DISPLAY | 文言・9 桁ゼロ埋め・stdout | §7.1 | 全ケースの stdout.txt |
| §7.1 既存 Java との対応 | `dailyInterest` 共有、`bearsInterest` は使わない、`BatchFileService` は変更しない | §2、§6 | 既存 39 テスト + 全ケース |
| §7.2-1 末尾空白 | COBOL（埋めない側）に合わせる | §4.2 | 全ケース |
| §7.2-2 桁あふれ | `numeric` の例外は使わない、WORK の 15 桁 | §5.3、§6 | `max-digits` |
| §7.2-3・4 未知の区分・空行 | 飛ばして RC=8 | §7.1 | `unknown-record-kind`・`blank-line` |
| §7.2-5 種目・状態コード | enum にしない、バイト比較 | §6 の最後 | `type-status-codes` |
| §7.2-6 トレーラ件数照合 | 読んだ時点の D 件数と比較 | §7.1 | `trailer-count-mismatch`・`records-after-trailer` |
| §7.2-7・8 短い・長いレコード | 空白埋め（数値項目が空白なら §7.2 の RC=12）・切捨て | §4.1、§7.2 | `long-record`、JUnit |
| §7.2-9 桁の数え方 | バイト単位 | §4.3 | `ms932-dbcs`（追加） |
| §7.2-10 LOG と終了コード | Java 版バッチで新しく持つ | §5.4、§7 | 全ケース |
| §7.2-11 残高 0 以下 | `dailyInterest` の分岐で一致 | §6 | `balance-zero` |
| §7.2-12 Javadoc の古い参照 | 直す | §6 | — |
| §8 #1–#5 | 移植時に決めること | §8 | — |
| §9 E01 | 現行ゴールデン | — | `baseline` |
| §9 E03–E08 | 桁あふれ | §5.3 | `max-digits` ほか（上の §4.4 行） |
| §9 E09–E16 | RC=8・RC=12・ヘッダ/トレーラの有無 | §3.4、§7.1 | 対応するゴールデン（ゴールデン README のケース一覧） |
| §9 E17 前半 / 後半 | 52 桁超 / 52 桁未満 | §4.1 / §7.2 | `long-record` / JUnit |
| §9 E18 | 残高が空白 | §7.2 | JUnit |
| §9 E19・E20 | CRLF・最終行改行なし | §4.1 | `crlf-input`・`no-final-newline` |
| §9 E21・E22 | FILLER・種目/状態 | §4.4、§6 | `filler-passthrough`・`type-status-codes` |
| §9 E23 | MS932 の 2 バイト文字 | §4.3 | `ms932-dbcs`（追加） |
| §9 E24 | DISPLAY は stdout、stderr なし | §7.1 | 全ケース（golden-lib.sh が stderr を参考表示） |
| §9 E25 | `run.sh` に RC=8 の入力 | §3.1（直す） | `batch/java/run.sh batch/golden/unknown-record-kind/ACCOUNTS.DAT` で `RC=8` と `GOLDEN MISMATCH` が出る |

---

## 10. テスト方針と後続ステップへの引き継ぎ

| ステップ | この設計から引き継ぐこと |
|---|---|
| s2.2 新旧比較ハーネス | `batch/golden/run-java.sh`（§3.3）と `batch/java/ubeod001.sh`（§3.1）。Java 版本体ができるまでは全ケース NG になることを確認する。ゴールデンに `ms932-dbcs`（E23）と `trailer-count-blank`（X09）を COBOL の `--update` で追加。`.gitignore` に `batch/golden/work-java/` と `batch/java/work/`。`batch/golden/README.md` の Java 版の使い方を書き換え |
| s3.1 固定長レコード I/O と数値編集 | `LineSequentialFile`・`HostRecord`・`Field`・`Pic`（§4・§5）と単体テスト。`Pic.store` は §12 X05 の値を、`LineSequentialFile` は §4.1 の表（X01–X04 を含む）をテストケースにする |
| s3.2 本体ロジック | `Ubeod001`・`Ubeod001Log`・`Ubeod001Abend`、`InterestService` の 3 引数版（§6）、`batch/java/run.sh`（§3.1）。`run-java.sh` で全ケース OK |
| s3.3 JUnit 組込み | `Ubeod001` の `run(dir, out)` で全ゴールデンを回す JUnit（名前は `*Test` か `*IT` にして surefire の対象にする）。§7.2 の Java 版だけのエラー（E17 後半・E18・NUL・入力がディレクトリ・出力不可・空白以外の数字以外が入ったトレーラ件数）は、期待する stdout・RC・出力なしを JUnit に直接書く。Jenkinsfile に Java 版のゴールデン比較を追加 |

---

## 11. COBOL と Java 版で結果が違うもの（意図したもの）

ゴールデンの 24 ケース（+ `ms932-dbcs`・`trailer-count-blank`）では違いはない。違うのは次の、COBOL の結果が再現できない・意味をなさない場合だけ。

| 入力・状況 | COBOL | Java 版 |
|---|---|---|
| 数値項目に数字以外（E17 後半・E18） | 意味のない値で RC=0 | RC=12、`NOT NUMERIC`、出力なし |
| トレーラ件数に空白以外の数字以外（例: `00000000A`） | 比較結果が GnuCOBOL 依存 | RC=12、`NOT NUMERIC TRL-COUNT` |
| 入力に NUL | 桁がずれた値で RC=0 | RC=12、`NUL BYTE` |
| ACCOUNTS.DAT がディレクトリ | 空ファイル扱いで RC=0 | RC=12、`OPEN ERROR 30` |
| 出力ファイルが作れない | 出力なしで RC=0 | RC=12、`WRITE ERROR 30` |
| `run.sh` | RC=8・12 で `RC=` も比較も出さずに終了（`set -e`）。既定の入力と期待値は `batch/cobol/data`・`expected`。引数の相対パスは `batch/cobol/` 基準 | RC=8・12 でも `RC=…` と `GOLDEN OK`/`MISMATCH` を出す。既定の入力と期待値は `batch/golden/baseline/`。引数の相対パスは呼び出し元基準 |

---

## 12. 実行確認の記録（本書で追加したもの）

仕様書 付録 A と同じ方法（GnuCOBOL 3.1.2 で `UBEOD001.cbl` をコンパイルし、作業ディレクトリに ACCOUNTS.DAT を置いて実行）。`G1` は仕様書 付録 A の明細 `D00110000011A000000001250000000001000000000000001234`。

| # | 入力 | COBOL の結果 |
|---|---|---|
| X01 | 明細の途中（9 バイト目）に CR | CR が消えて通常の明細として計算（`…01237`）、RC=0 |
| X02 | 行末が `\r\r\n` / 改行が CR だけ（LF なし） | `\r\r\n`: LF と同じ出力。CR だけ: ファイル全体が 1 レコード（CR を除いて 53 バイト目以降を捨てた `H20180315D0011…` が H として出力） |
| X03 | 明細の 9 バイト目に NUL | 出力が `D0011000^@0011A0000000012500…` と 1 バイトずれ、状態が `1` になって対象外、RC=0 |
| X04 | 明細の 9 バイト目に 0x01・0x02・0x07・0x08・0x0B・0x0C・0x1B・0x1F・0x7F・0xFF | どれもそのバイトのまま出力され、通常どおり計算（`…01237`）、RC=0 |
| X05 | JShell で `setScale(m, DOWN).remainder(10^n)` と §6 の式 | E03 = 2739723287671.20、E04 = 2739725479452.05、E05 = 273972602739.72、基準値 1250000 × 0.0010% = 0.03、E06 = 9.99、E07 = 999999999999899、E08 = 999999999999999.98（仕様書の COBOL の結果とすべて一致） |
| X06 | ACCOUNTS.DAT を読めない（`chmod 000`）/ ディレクトリ | 読めない: `UBEOD001E ACCOUNTS.DAT OPEN ERROR 37`、RC=12。ディレクトリ: 0 件の空ファイル扱いで `COMPLETED. READ=000000000 …`、RC=0 |
| X07 | ACCRUED.DAT という名前のディレクトリがある | ACCRUED.DAT は書かれず、メッセージなしで RC=0（LOG は書かれる） |
| X08 | 状態 C の明細で利率が `ABCDEFG` | 利率はそのまま出力、未払利息合計は 12.34 で正常、RC=0 |
| X09 | トレーラが `T` だけ | `UBEOD001E TRAILER COUNT MISMATCH IN=          READ=000000001`、RC=8 |

確認に使ったコマンド（抜粋）:

```bash
cobc -x -o ubeod001 batch/cobol/UBEOD001.cbl
G1=D00110000011A000000001250000000001000000000000001234
printf 'H20180315\nD0011000\r0011A000000001250000000001000000000000001234\nT000000001\n' > ACCOUNTS.DAT; ./ubeod001   # X01
printf 'H20180315\nD0011000\0000011A000000001250000000001000000000000001234\nT000000001\n' > ACCOUNTS.DAT; ./ubeod001  # X03
for o in 001 002 007 010 013 014 033 037 177 377; do                                                                # X04
  printf "H20180315\nD0011000\\${o}011A000000001250000000001000000000000001234\nT000000001\n" > ACCOUNTS.DAT; ./ubeod001; done
printf 'H20180315\nD00110000011C000000001250000ABCDEFG00000000000001234\nT000000001\n' > ACCOUNTS.DAT; ./ubeod001     # X08
printf 'H20180315\n%s\nT\n' $G1 > ACCOUNTS.DAT; ./ubeod001                                                          # X09
```

クラスパス起動の確認（§3.2）:

```console
$ JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 mvn -B -q -DskipTests compile dependency:build-classpath \
    -Dmdep.includeScope=runtime -Dmdep.outputFile=/tmp/cp.txt          # real 0m4.2s
$ java -cp out:target/classes:$(cat /tmp/cp.txt) jp.usagi.bank.batch.host.P; echo "exit=$?"
0.03                                                                    # InterestService.dailyInterest(1250000, 0.0010)
exit=8                                                                  # System.exit(8) がそのまま返る (real 0m0.13s)
$ java -cp out:target/classes jp.usagi.bank.batch.host.P
Exception in thread "main" java.lang.NoClassDefFoundError: org/slf4j/LoggerFactory
```

（`P` は確認用の 3 行のクラスで、リポジトリには追加していない）
