# UBEOD001 ゴールデンセット

COBOL `batch/cobol/UBEOD001.cbl` を正として、境界・異常系を含む入力ごとに GnuCOBOL で実行した結果を期待値として固めたもの。
Java 版 UBEOD001 も同じケース・同じ比較処理（`golden-lib.sh`）で照合する。挙動の根拠は [`docs/migration/UBEOD001-spec.md`](../../docs/migration/UBEOD001-spec.md)（以下「仕様書」）。

## 構成

```
batch/golden/
  run-cobol.sh        全ケースを COBOL で実行し、期待値とバイト比較する
  golden-lib.sh       比較処理の本体（COBOL 版・Java 版で共用）
  <ケース名>/
    ACCOUNTS.DAT      入力（ないケース = 入力ファイルなし）
    ACCRUED.DAT       期待値: 出力ファイル（ない = 作られないことが期待値）
    UBEOD001.LOG      期待値: 処理件数レポート（同上）
    stdout.txt        期待値: 標準出力（DISPLAY メッセージ）
    rc.txt            期待値: 終了コード
  work/               実行時の作業ディレクトリ（git 管理外）
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
結果: OK=24 NG=0 (全 24 ケース)
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

### 入れていないもの

次の挙動は再現の対象にしない、または移植方針が未定なのでケースにしていない。

- 数値項目に数字以外が入る入力（52 桁未満の明細、残高が空白など。仕様書 E17 後半・E18）: 出力値が GnuCOBOL の内部実装に依存する（仕様書 §4.5）。
- ASCII 以外のバイト（MS932 の 2 バイト文字。仕様書 E23）: バイト単位で処理するかエラーにするかが未定（仕様書 §8 #3）。
- 出力ファイルの OPEN・WRITE エラー: COBOL が判定していない（仕様書 §8 #4）。

## Java 版での使い方（予定）

`golden-lib.sh` の `golden_main` に実行コマンドを渡せば、同じケースを同じ手順で比較できる。

```bash
. batch/golden/golden-lib.sh
GOLDEN_DIR=batch/golden GOLDEN_WORK=batch/golden/work-java \
  golden_main "$@" -- java -jar /path/to/ubeod001.jar
```

コマンドは作業ディレクトリをカレントにして実行されるので、COBOL と同じくカレントの `ACCOUNTS.DAT` を読み、`ACCRUED.DAT`・`UBEOD001.LOG` を書き、stdout にメッセージを出し、終了コードを返すこと。
