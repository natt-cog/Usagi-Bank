# UBEOD001 ゴールデンセット

COBOL `batch/cobol/UBEOD001.cbl` を正として、境界・異常系を含む入力ごとに GnuCOBOL で実行した結果を期待値として固めたもの。
Java 版 UBEOD001 も同じケース・同じ比較処理（`golden-lib.sh`）で照合する。挙動の根拠は [`docs/migration/UBEOD001-spec.md`](../../docs/migration/UBEOD001-spec.md)（以下「仕様書」）。

## 構成

```
batch/golden/
  run-cobol.sh        全ケースを COBOL で実行し、期待値とバイト比較する
  run-java.sh         全ケースを Java 版（batch/java/ubeod001.sh）で実行し、期待値とバイト比較する
  compare.sh          新旧比較ハーネス: COBOL 版と Java 版を同じ入力で実行し、ケースごとの一致 / 不一致を表で出す
  integration.sh      ファイル連携の結合テスト: アプリの API → COBOL 版 / Java 版 → アプリの API を新旧両方で通す
  golden-lib.sh       比較処理の本体（run-cobol.sh・run-java.sh・compare.sh で共用）
  <ケース名>/
    ACCOUNTS.DAT      入力（ないケース = 入力ファイルなし）
    ACCRUED.DAT       期待値: 出力ファイル（ない = 作られないことが期待値）
    UBEOD001.LOG      期待値: 処理件数レポート（同上）
    stdout.txt        期待値: 標準出力（DISPLAY メッセージ）
    rc.txt            期待値: 終了コード
  work/               run-cobol.sh の作業ディレクトリ（git 管理外）
  work-java/          run-java.sh の作業ディレクトリ（git 管理外）
  work-compare/       compare.sh の作業ディレクトリ（git 管理外）
  work-integration/   integration.sh の作業ディレクトリ（git 管理外）
```

比較の対象は仕様書 §8 の「バイト一致が必要な範囲」である ACCRUED.DAT・UBEOD001.LOG・stdout・終了コードの 4 つ。stderr は比較しない（出力があれば参考として表示する）。

## 実行

GnuCOBOL の `cobc` が必要（期待値は cobc 3.1.2 で作成）。

```bash
batch/golden/run-cobol.sh                       # 全ケース
batch/golden/run-cobol.sh baseline no-input     # 指定したケースだけ
```

```
OK   accrued-near-overflow (RC=0)
...
OK   unknown-record-kind (RC=8)
----
結果: OK=26 NG=0 (全 26 ケース)
```

終了コードは 0 = 全ケース一致、1 = 不一致あり、2 = コンパイル失敗。不一致のときは、どのファイルがどう違うかを `cat -A` 表示の diff で出す（左が期待値、右が実行結果）。

各ケースは空の作業ディレクトリ `work/<ケース名>/` に ACCOUNTS.DAT だけをコピーし、そこをカレントにして実行する。プログラム自身の終了コードを `rc.txt` に取るので、RC=8・RC=12 のケースも最後まで比較する（`batch/cobol/run.sh` は `set -e` のため RC≠0 だと比較まで進まない。仕様書 E25）。

## 期待値の生成手順

期待値はすべて COBOL の実行結果そのもので、手で書いたものはない。

1. ケースのディレクトリ `batch/golden/<ケース名>/` を作り、入力 `ACCOUNTS.DAT` を置く（入力ファイルなしのケースは空のディレクトリ）。
2. COBOL で実行して期待値を書き出す:
   ```bash
   batch/golden/run-cobol.sh --update <ケース名>    # 引数なしなら全ケース
   ```
   `--update` は比較をせず、実行結果の ACCRUED.DAT・UBEOD001.LOG・stdout.txt・rc.txt をケースのディレクトリにコピーする。出力されなかったファイルは期待値からも消す。
3. `git diff` で出力が仕様書どおりかを確かめる（`cat -A` で末尾空白・改行も見る）。
4. `batch/golden/run-cobol.sh` で全ケース OK になることを確かめてコミットする。

`.gitattributes` で `batch/golden/**` は `-text`（改行変換なし）にしてある。CRLF の入力や最終行に改行がない入力をそのまま保つため。

`baseline` は `batch/cobol/data/ACCOUNTS.DAT` のコピーで、期待値の ACCRUED.DAT・UBEOD001.LOG は `batch/cobol/expected/` と同じ内容（`cmp` で確認済み）。

## ケース一覧

入力のヘッダ・トレーラは `exportAccounts` と同じく 52 桁に空白で埋めてある（`filler-passthrough` などを除く）。「E」は仕様書 §9 の実行確認番号。

| ケース | 入力 | RC | 見るところ | 仕様書 |
|---|---|---|---|---|
| `baseline` | 現行ゴールデン（15 明細） | 0 | 通常の計算 | E01 |
| `rate-zero` | 利率 0（種目 1/4/9、残高最大を含む） | 0 | 利息対象に数えるが日次利息 0.00 | §4.2 |
| `balance-zero` | 残高 0（利率 999.9999 を含む） | 0 | 日次利息 0.00 | §7.2-11 |
| `status-fdc` | 状態 F/D/C だけ | 0 | 全件対象外、未払利息は変わらない | §4.1 |
| `current-only` | 当座（種目 2）だけ（状態 A/F/C） | 0 | 全件対象外 | §4.1 |
| `max-digits` | 残高 999999999999999・利率 999.9999 ほか | 0 | `WS-WORK` 9(15) の上位桁切捨て、上限ちょうど | E03–E05 |
| `accrued-near-overflow` | 未払利息 999999999999989.99 + 10.00 | 0 | 999999999999999.99 ちょうどに収まる | §4.4 |
| `accrued-overflow` | 未払利息 999999999999999.99 + 10.00 | 0 | 上位桁が落ちて 9.99 | E06 |
| `total-balance-overflow` | 残高 999999999999999 × 101 件 | 0 | 残高合計 9(17) の桁あふれ | E07 |
| `total-accrued-overflow` | 未払利息 999999999999999.99 × 2 件 | 0 | 未払利息合計 9(15)V9(2) の桁あふれ | E08 |
| `trailer-count-mismatch` | 明細 2 件、トレーラ件数 5 | 8 | 続行して再集計値のトレーラを出力 | E09 |
| `trailer-count-blank` | 明細 2 件、トレーラは `T` のあと全部空白（件数が空白 9 バイト） | 8 | `IN=` のあとが空白 9 バイトの MISMATCH、続行して再集計値のトレーラを出力 | 設計メモ X09 |
| `unknown-record-kind` | 区分 `X` の行 | 8 | その行は出力せず件数にも入れない | E10 |
| `blank-line` | 空行 | 8 | 区分が空白 → 未知の区分扱い | E11 |
| `header-trailer-only` | ヘッダとトレーラだけ | 0 | 件数・合計 0 のトレーラ | §5 |
| `empty-file` | 0 バイト | 0 | ACCRUED.DAT は 0 バイト、LOG の処理日 0 | E14 |
| `no-input` | ACCOUNTS.DAT なし | 12 | ACCRUED.DAT・LOG を作らない、完了メッセージなし | E12 |
| `no-header` | ヘッダなし | 0 | LOG の処理日 0 | E13 |
| `no-trailer` | トレーラなし | 0 | トレーラを出力しない | E15 |
| `records-after-trailer` | トレーラの後に 2 個目のヘッダと明細 | 0 | カウンタは通算、LOG の処理日は後のヘッダ | E16 |
| `filler-passthrough` | ヘッダ・トレーラの FILLER に文字 | 0 | FILLER は入力の値がそのまま残る | E21 |
| `type-status-codes` | 種目 2/4/9/3/空白、状態 F/空白 | 0 | 種目は「2 以外」、状態は「A だけ」 | E22 |
| `crlf-input` | CRLF 改行 | 0 | LF と同じ出力 | E19 |
| `no-final-newline` | 最終行に改行なし | 0 | LF と同じ出力 | E20 |
| `long-record` | 52 桁を超える明細 | 0 | 53 桁目以降を捨てる | E17 |
| `ms932-dbcs` | 口座番号に MS932 の 2 バイト文字（`ア` = 0x83 0x41） | 0 | バイト単位で桁を切り、2 バイト文字はそのまま出力。利息も通常どおり | E23 |

### 入れていないもの

次の挙動は再現の対象にしない、または移植方針が未定なのでケースにしていない。

- 数値項目に数字以外が入る入力（52 桁未満の明細、残高が空白など。仕様書 E17 後半・E18）: 出力値が GnuCOBOL の内部実装に依存する（仕様書 §4.5）。
- 出力ファイルの OPEN・WRITE エラー: COBOL が判定していない（仕様書 §8 #4）。

## Java 版での使い方

Java 版 UBEOD001 の起動コマンドは `batch/java/ubeod001.sh`（設計メモ [§3.1](../../docs/migration/UBEOD001-java-design.md)）。COBOL の実行ファイルと同じく、カレントの `ACCOUNTS.DAT` を読み、`ACCRUED.DAT`・`UBEOD001.LOG` を書き、stdout にメッセージだけを出し、終了コード = RC。ビルド失敗・JDK 8 以外・未実装は終了コード 2。JDK 8 と Maven が必要。

```bash
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
batch/golden/run-java.sh                        # 全ケース (Java 版 ↔ 期待値)
batch/golden/run-java.sh baseline no-input      # 指定したケースだけ
```

- 最初に `ubeod001.sh --build-only` で 1 回だけ `mvn compile` し、各ケースでは `ubeod001.sh` を引数なしで呼ぶ。
- 作業ディレクトリは `work-java/`。COBOL（`work/`）と続けて実行しても互いの結果を消さない。
- **Java 版で `--update` しない。** 期待値は COBOL の実行結果だけで作る。
- Java 版本体（`src/main/java/jp/usagi/bank/batch/host/Ubeod001.java`）ができるまでは、全ケース NG（RC=2、stderr に「未実装」）になる。

## JUnit（mvn test）

GnuCOBOL がない環境でも同値性を守れるよう、`mvn test` に次の 2 つのテストを入れてある。COBOL は呼ばず、このディレクトリの期待値（COBOL で作ったもの）だけを使う。

| テスト | 内容 |
|---|---|
| `Ubeod001GoldenTest` | このディレクトリの全ケース（`work`・`work-*` を除く）を `Ubeod001.run(dir, out)` で一時ディレクトリで実行し、ACCRUED.DAT・UBEOD001.LOG・stdout・RC を期待値とバイト比較する。期待値ファイルがないものは作られないこと、一時ファイル（`*.tmp`）が残らないことも確かめる。ケースを足すと自動で対象になる |
| `Ubeod001Test` | ゴールデンに入れていない Java 版だけのエラー（設計メモ §7.2、RC=12）: 数値項目に数字以外（E17 後半・E18 ほか）、トレーラ件数に空白以外の数字以外、NUL、入力がディレクトリ・読めない、出力ファイルが作れない・置き換えられない。期待する stdout・RC・出力なしをテストに直接書いている |

```bash
JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 mvn -B test -Dtest='Ubeod001*'
```

## 新旧比較ハーネス（compare.sh）

COBOL 版と Java 版を同じ入力で実行し、ケースごとに次の 3 つを比べて表（Markdown）で出す。比較は `golden-lib.sh` の `golden_compare_case`（ACCRUED.DAT・UBEOD001.LOG・stdout・RC のバイト比較）をそのまま使う。

| 列 | 比べるもの |
|---|---|
| COBOL ↔ 期待値 | COBOL 版の実行結果と、このディレクトリの期待値 |
| Java ↔ 期待値 | Java 版の実行結果と、このディレクトリの期待値 |
| 新旧 (Java ↔ COBOL) | Java 版の実行結果と、同じ実行で得た COBOL 版の実行結果 |

```bash
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
batch/golden/compare.sh                         # 全ケース
batch/golden/compare.sh baseline ms932-dbcs     # 指定したケースだけ
batch/golden/compare.sh --require-java          # Java 版が未実装なら終了コード 3 (p3・Jenkins ではこれを使う)
```

```
| ケース | 期待 RC | COBOL RC | COBOL ↔ 期待値 | Java RC | Java ↔ 期待値 | 新旧 (Java ↔ COBOL) |
|---|---|---|---|---|---|---|
| `accrued-near-overflow` | 0 | 0 | 一致 | - | 未実装 | 未実装 |
...
----
COBOL ↔ 期待値: 一致=26 不一致=0 (全 26 ケース)
Java          : 未実装 (src/main/java/jp/usagi/bank/batch/host/Ubeod001.java がない。COBOL ↔ 期待値だけで判定)
```

- 終了コード: 0 = 不一致なし、1 = 1 件でも不一致あり、2 = コンパイル・ビルド失敗など、3 = `--require-java` を付けたのに Java 版が未実装（不一致があれば 1 を優先）。不一致があれば表のあとに `cat -A` 表示の diff を出す（新旧の diff は左が COBOL、右が Java）。
- Java 版本体がまだないときは Java・新旧の列を「未実装」とし、COBOL ↔ 期待値だけで判定する（全ケース一致なら終了コード 0）。**p3 の各ステップと Jenkins では `--require-java` を付ける**。付けると、Java の列に「未実装」があれば終了コード 3 になる（「未実装」のまま合格扱いにしない）。
- COBOL 版は `batch/cobol/run.sh` ではなく、`run-cobol.sh` と同じく `cobc` でビルドした実行ファイルを直接呼ぶ。`run.sh` は `set -e` のため RC≠0（RC=8・12）のケースで比較まで進まないため（仕様書 E25）。
- `cobc`（GnuCOBOL）が必要。COBOL を消すフェーズ（p5）以降は `run-java.sh` を使う。
- 作業ディレクトリは `work-compare/cobol/<ケース名>/`・`work-compare/java/<ケース名>/`。実行のたびに作り直す。
- `--update` は受け付けない（期待値は `run-cobol.sh --update` で作る）。

## ファイル連携の結合テスト（integration.sh）

ゴールデンセットはバッチ単体の比較。`integration.sh` は実際の連携経路（アプリが出す ACCOUNTS.DAT → ホストバッチ → アプリへの ACCRUED.DAT 取込）を COBOL 版・Java 版の両方で通し、取込結果が同じであることを確かめる。アプリは自分で起動・停止する（`mvn spring-boot:run`、H2 は起動ごとに初期化）。

```bash
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
batch/golden/integration.sh                     # 1 分ほど。ポート 8080 を空けておく
```

| 手順 | 内容 |
|---|---|
| 1 | アプリを起動し、`GET /api/batch/accounts-file`（HTTP Basic、admin）で ACCOUNTS.DAT を取得 |
| 2 | 同じ ACCOUNTS.DAT を `batch/cobol/run.sh` と `batch/java/run.sh` に流し、ACCRUED.DAT・UBEOD001.LOG・stdout・RC が新旧バイト一致することを確かめる |
| 3 | Java 版の ACCRUED.DAT を `POST /api/batch/accrued-file` で取り込み、更新件数、各口座の未払利息、`GET /api/accounts` を記録・確認 |
| 4 | アプリを再起動し、ACCOUNTS.DAT が手順 1 と同じ（H2 が初期化された）ことを確かめてから、COBOL 版の ACCRUED.DAT で手順 3 と同じことをする |
| 5 | 新旧の取込結果（処理日・更新件数・スキップ件数、各口座の未払利息、`GET /api/accounts`）を比べ、口座ごとの表を出す。未払利息の増分の合計が UBEOD001.LOG の当日利息合計と同じことも確かめる |

- 未払利息は `GET /api/accounts` の JSON（`AccountDto`）に含まれないため、取込後に `GET /api/batch/accounts-file` で再取得して見る。再取得したファイルは、行末空白を除くと取り込んだ ACCRUED.DAT とバイト一致する（未払利息とトレーラの合計を含む）ことも確かめる。
- API の ACCOUNTS.DAT は処理日（ヘッダ）が業務日付（実行日）になるので、`run.sh` 自身の `GOLDEN OK / MISMATCH`（golden/baseline との比較）は MISMATCH・終了コード 1 になる。判定には使わず、代わりに「baseline の期待値の処理日を差し替えたもの」と一致するかを参考として出す。
- `batch/cobol/run.sh` は `set -e` のため RC≠0 だと `RC=` を出さずに終わる（仕様書 E25）。そのときは `run.sh` の終了コードを RC として記録し、RC≠0 は NG にする。
- 終了コード: 0 = 新旧一致、1 = 不一致あり、2 = 起動失敗など（ポート使用中、ビルド失敗、API エラー）。
- `cobc`・JDK 8・Maven・curl・jq・bc が必要。COBOL を消すフェーズ（p5）以降はこのスクリプトは使えない。
