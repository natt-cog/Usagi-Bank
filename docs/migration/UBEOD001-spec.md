# UBEOD001 仕様書（COBOL → Java 移植用）

対象: `batch/cobol/UBEOD001.cbl`（預金口座 日次利息積数計算、1998 年初版）
目的: 移植者がこの文書だけで、現行 COBOL と **バイト単位で同じ出力** を返す Java 版を書けるようにする。
確認環境: GnuCOBOL `cobc (GnuCOBOL) 3.1.2.0`（この VM）、JDK 8。`Jenkinsfile` のコメントでは CI エージェントは `cobc 2.2` となっているが、本書の実行結果はすべて 3.1.2 で取得した。

> 凡例: **[実行確認]** は実際に動かして確かめた項目。コマンドと出力は §9 と付録 A/B にある。印のない項目はソースを読んで得た内容。

---

## 1. 概要

| 項目 | 内容 |
|---|---|
| プログラム ID | `UBEOD001` |
| 入力 | `ACCOUNTS.DAT`（カレントディレクトリ、LINE SEQUENTIAL、52 桁） |
| 出力 | `ACCRUED.DAT`（LINE SEQUENTIAL、52 桁）、`UBEOD001.LOG`（LINE SEQUENTIAL、80 桁） |
| 標準出力 | `DISPLAY` メッセージ（§6）。stderr には何も出さない **[実行確認 E24]** |
| 終了コード | 0 / 8 / 12（§6） |
| 処理 | 有効（状態 `A`）かつ当座（種目 `2`）以外の口座に、日次利息 = 残高 × 年利(%) ÷ 100 ÷ 365（銭未満切捨て）を未払利息へ加算して書き出す |
| 位置づけ | オンライン側 `GET /api/batch/accounts-file`（`BatchFileService.exportAccounts`）で作った `ACCOUNTS.DAT` を処理し、結果の `ACCRUED.DAT` を `POST /api/batch/accrued-file`（`BatchFileService.importAccrued`）で取り込むホスト側ステップ |

ファイル名はプログラム内に固定（`ASSIGN TO "ACCOUNTS.DAT"` など）。引数や環境変数では変えられない。`run.sh` は `work/` に入力をコピーし、そのディレクトリで実行する。

### 1.1 処理の流れ（PROCEDURE DIVISION）

```
0000-MAIN
  1000-OPEN   : ACCOUNTS.DAT を OPEN INPUT。FILE STATUS ≠ "00" なら DISPLAY, RC=12, STOP RUN
                ACCRUED.DAT / UBEOD001.LOG を OPEN OUTPUT（エラー判定なし）
                1 件目を READ
  2000-PROCESS: EOF まで繰り返し。レコード区分（1 桁目）で分岐
                "H" → 2200-HEADER / "D" → 2300-DETAIL / "T" → 2400-TRAILER
                それ以外 → DISPLAY, RC=8（そのレコードは出力しない。処理は続ける）
                次を READ
  3000-CLOSE  : LOG を 7 行書き、全ファイル CLOSE、完了メッセージを DISPLAY
  STOP RUN
```

- 入力の並び順は検査しない。H/D/T は出現した順に処理し、出現した順に書き出す。トレーラの後のレコードやヘッダの 2 回目も普通に処理される **[実行確認 E16]**。
- ヘッダ・トレーラは無くてもよい。無ければ出力にも出ない **[実行確認 E13, E15]**。
- 空ファイルなら `ACCRUED.DAT` は 0 バイトで作られ、LOG は全項目 0 で出る **[実行確認 E14]**。

---

## 2. ファイルレイアウト

桁位置は 1 始まりのバイト位置。Java の `String#substring` で使う 0 始まりの範囲も併記する（ASCII 前提、§3.3）。

### 2.1 ACCOUNTS.DAT（入力）/ ACCRUED.DAT（出力）共通 — 52 桁

FD 上は `ACCT-REC` = `ACCT-KIND X(01)` + `ACCT-BODY X(51)`、出力は `OUT-REC X(52)`。区分ごとのレイアウトは WORKING-STORAGE の `WS-HDR` / `WS-DTL` / `WS-TRL` で定義される。

#### ヘッダ（H）— `WS-HDR`

| 桁位置 | substring | 項目 | PIC | 内容 |
|---|---|---|---|---|
| 1 | 0–1 | `WS-HDR-KIND` | X(01) | `H` |
| 2–9 | 1–9 | `WS-HDR-DATE` | 9(08) | 処理日 YYYYMMDD |
| 10–52 | 9–52 | FILLER | X(43) | 空白 |

#### データ（D）— `WS-DTL`

| 桁位置 | substring | 項目 | PIC | 内容 |
|---|---|---|---|---|
| 1 | 0–1 | `WS-DTL-KIND` | X(01) | `D` |
| 2–4 | 1–4 | `WS-DTL-BRANCH` | X(03) | 店番 |
| 5–11 | 4–11 | `WS-DTL-ACCT-NO` | X(07) | 口座番号 |
| 12 | 11–12 | `WS-DTL-TYPE` | X(01) | 預金種目（全銀コード 1=普通 2=当座 4=貯蓄 9=定期） |
| 13 | 12–13 | `WS-DTL-STATUS` | X(01) | 口座状態（A=有効 F=凍結 D=休眠 C=解約） |
| 14–28 | 13–28 | `WS-DTL-BALANCE` | 9(15) | 残高（円、符号なし、ゼロ埋め） |
| 29–35 | 28–35 | `WS-DTL-RATE` | 9(03)V9(04) | 年利 %。小数点は書かない（`0010000` = 1.0000%、`0000010` = 0.0010%） |
| 36–52 | 35–52 | `WS-DTL-ACCRUED` | 9(15)V9(02) | 未払利息（銭単位、小数点なし。`00000000000001234` = 12.34 円） |

出力では `WS-DTL-ACCRUED` だけが更新される（利息対象のとき）。他の項目は入力のバイトがそのまま出る。

#### トレーラ（T）— `WS-TRL`

| 桁位置 | substring | 項目 | PIC | 入力での扱い | 出力の値 |
|---|---|---|---|---|---|
| 1 | 0–1 | `WS-TRL-KIND` | X(01) | — | `T` |
| 2–10 | 1–10 | `WS-TRL-COUNT` | 9(09) | `WS-READ-COUNT` との照合だけに使う | `WS-READ-COUNT`（そこまでに読んだ D 件数） |
| 11–27 | 10–27 | `WS-TRL-BALANCE` | 9(17) | 読まない | `WS-TOTAL-BALANCE`（D 残高合計） |
| 28–44 | 27–44 | `WS-TRL-ACCRUED` | 9(15)V9(02) | 読まない | `WS-TOTAL-ACCRUED`（加算後の未払利息合計、銭単位） |
| 45–52 | 44–52 | FILLER | X(08) | — | 入力トレーラの 45–52 桁をそのまま引き継ぐ |

#### FILLER の引き継ぎ **[実行確認 E21]**

- ヘッダは `WRITE OUT-REC FROM ACCT-REC` で **入力行をそのまま** 書く。10–52 桁に何か入っていればそのまま出る（`H20180315 FILLERXX` → `H20180315 FILLERXX`）。
- トレーラは `MOVE ACCT-REC TO WS-TRL` の後で 1–44 桁を上書きするので、45–52 桁は入力のまま出る（`TRLFILL` が残る）。
- 通常の入力ではどちらも空白なので、末尾空白の除去（§3.1）で消える。

### 2.2 UBEOD001.LOG（出力）— 80 桁

固定 7 行。`WS-LOG-LINE`（件数行）と `WS-LOG-AMT-LINE`（金額行）を使う。

| 行 | 種類 | ラベル（1–30 桁、`X(30)`） | 値 | 編集 PIC / 桁位置 |
|---|---|---|---|---|
| 1 | 見出し | `UBEOD001 NICHIJI RISOKU SEKISUU`（`LOG-REC` へ直接 MOVE） | — | — |
| 2 | 件数行 | `SHORI-BI (YYYYMMDD)` | `WS-HDR-DATE`（最後に読んだヘッダの日付。ヘッダなしなら 0） | `Z(16)9`、31–47 桁 |
| 3 | 件数行 | `YOMIKOMI KENSUU` | `WS-READ-COUNT` | `Z(16)9`、31–47 桁 |
| 4 | 件数行 | `RISOKU KEISAN KENSUU` | `WS-ACCRUED-COUNT` | `Z(16)9`、31–47 桁 |
| 5 | 件数行 | `TAISHOUGAI KENSUU` | `WS-SKIPPED-COUNT` | `Z(16)9`、31–47 桁 |
| 6 | 金額行 | `TOUJITSU RISOKU GOUKEI (YEN)` | `WS-TOTAL-DAILY`（当日利息合計） | `Z(14)9.99`、31–48 桁 |
| 7 | 金額行 | `MIBARAI RISOKU GOUKEI (YEN)` | `WS-TOTAL-ACCRUED`（未払利息合計） | `Z(14)9.99`、31–48 桁 |

- ラベルは 30 桁に空白で右埋めし、値は右寄せ。件数行 = 30 + 17 + FILLER 33 = 80、金額行 = 30 + 18 + FILLER 32 = 80。
- `Z(16)9`: 先頭のゼロを空白に置き換えた 17 桁。最下位の 1 桁は必ず数字（0 は `                0`）。
- `Z(14)9.99`: 整数部 15 桁（上位 14 桁はゼロを空白に）、`.`、小数 2 桁。0 は `              0.00`。
- 出力時に末尾空白が落ちるので、実際の行長は 31 / 47 / 47 / 47 / 47 / 48 / 48 バイトになる **[実行確認 E01]**。
- 行 1 のリテラルは末尾に空白 2 個を含む（`"UBEOD001 NICHIJI RISOKU SEKISUU  "`）が、これも落ちる。
- `run.sh` は LOG を `expected/UBEOD001.LOG` と比較していない。ただし両者は一致する **[実行確認 E01]**。

Java での組み立て例（末尾空白除去前）:

```java
String countLine = String.format("%-30s%17d", label, value);          // + 33 空白
String amtLine   = String.format("%-30s%18s", label, amount.toPlainString()); // amount は scale 2
```

---

## 3. LINE SEQUENTIAL の挙動

### 3.1 出力: 末尾空白を落とす **[実行確認 E01]**

GnuCOBOL の LINE SEQUENTIAL は `WRITE` 時にレコード末尾の空白を取り除き、`\n` を付けて書く。

- `ACCRUED.DAT` のヘッダは 52 桁ではなく **`H20180315` の 9 バイト + LF** だけ。
- トレーラは 44 バイト + LF（45–52 桁の FILLER が空白のため）。
- D レコードは最後の項目が数字なので常に 52 バイト + LF。
- `expected/ACCRUED.DAT` は 850 バイト = 10（H）+ 53 × 15（D）+ 45（T）。
- LOG も同じ（§2.2）。
- 途中の空白は残る（例: 種目が空白の D レコード `D0011000001 A…` はそのまま出る **[実行確認 E22]**）。

移植時の規則: **各レコードを 52 桁（LOG は 80 桁）で組み立て、末尾の空白（0x20）だけを除いて LF を付ける**。

### 3.2 入力 **[実行確認 E17, E19, E20, E11]**

| 入力の状態 | COBOL の挙動 |
|---|---|
| 52 桁未満の行（例: ヘッダが `H20180315` だけ） | 右側を空白で埋めて 52 桁として扱う。入力ゴールデンのヘッダ・トレーラは 52 桁に空白で埋めてあるが、埋めていなくても結果は同じ |
| 52 桁を超える行 | 53 桁目以降を捨てる（エラーにしない） |
| CRLF 改行 | CR は取り除かれ、LF 改行と同じ結果になる |
| 最終行に改行なし | 普通に 1 行として読む |
| 空行 | 1 桁目が空白 → 未知のレコード区分として RC=8（§6） |

### 3.3 改行コードと文字コード

- 出力の改行は **LF（0x0A）のみ**。CR は出さない（`grep -c $'\r' ACCRUED.DAT` = 0）**[実行確認 E01]**。
- COBOL は **バイト単位** で処理し、文字コードを変換しない。連携仕様上の文字コードは MS932（`BatchFileService.HOST_CHARSET`）だが、全項目が英数字なので実データは **ASCII のみ**。
- MS932 の 2 バイト文字が入ると、COBOL は 52 **バイト** として桁位置を数える **[実行確認 E23]**。Java で MS932 として読むと 1 文字に縮むので `substring` の位置がずれる（付録 B: 12 バイト → 11 文字）。移植版は「ASCII 以外が来たらエラー」にするか、バイト列（または ISO-8859-1）で桁を切ること。

---

## 4. 計算仕様

### 4.1 利息対象の判定 **[実行確認 E22]**

```cobol
IF WS-DTL-STATUS = "A" AND WS-DTL-TYPE NOT = "2"
```

- 状態が `A` **かつ** 種目が `2` 以外なら利息対象。
- 種目は「`2` 以外」の判定なので、`1`/`4`/`9` だけでなく、全銀コードにない `3` や空白でも対象になる。
- 状態は `A` だけが対象。`F`/`D`/`C`/空白などはすべて対象外。
- 対象 → `WS-ACCRUED-COUNT` +1、対象外 → `WS-SKIPPED-COUNT` +1。どちらでもレコードは出力する。

### 4.2 日次利息

```cobol
COMPUTE WS-WORK = WS-DTL-BALANCE * WS-DTL-RATE / 100
COMPUTE WS-DAILY-INT ROUNDED MODE TRUNCATION = WS-WORK / WS-DAYS-IN-YEAR
ADD WS-DAILY-INT TO WS-DTL-ACCRUED
ADD WS-DAILY-INT TO WS-TOTAL-DAILY
```

| 変数 | PIC | 意味 |
|---|---|---|
| `WS-WORK` | 9(15)V9(10) | 中間値。整数 15 桁・小数 10 桁 |
| `WS-DAYS-IN-YEAR` | 9(03) VALUE 365 | 閏年も 365 |
| `WS-DAILY-INT` | 9(15)V9(02) | 日次利息（銭単位） |

1. `WS-WORK = 残高 × 利率 ÷ 100`。GnuCOBOL は右辺を内部 10 進数（任意精度）で計算し、`WS-WORK` に入れるときに小数 11 桁目以降を切り捨てる（`ROUNDED` なし）。残高は整数、利率は小数 4 桁なので積 ÷ 100 は小数 6 桁までで、小数部の切捨ては実際には起きない。
2. `WS-DAILY-INT = WS-WORK ÷ 365` を **小数 2 桁で切捨て**（`ROUNDED MODE TRUNCATION`。`ROUNDED` を省いた場合と同じ結果）。
3. 未払利息と当日利息合計に加算する。

Java での等価式（`InterestService.dailyInterest` と同じ）:

```java
BigDecimal work  = balance.multiply(rate).divide(HUNDRED, 10, RoundingMode.DOWN);
BigDecimal daily = work.divide(DAYS_IN_YEAR, 2, RoundingMode.DOWN);
```

ゴールデンの例: 残高 1,250,000、利率 0.0010% → WORK = 12.5 → 12.5 / 365 = 0.0342… → **0.03**（`…01234` → `…01237`）。

### 4.3 集計

| 変数 | PIC | 加算するタイミング |
|---|---|---|
| `WS-READ-COUNT` | 9(09) | D を読むたび +1 |
| `WS-TOTAL-BALANCE` | 9(17) | D を読むたび残高を加算（対象外も含む） |
| `WS-TOTAL-DAILY` | 9(15)V9(02) | 利息対象の日次利息 |
| `WS-TOTAL-ACCRUED` | 9(15)V9(02) | 全 D の **加算後** 未払利息（対象外も含む） |
| `WS-ACCRUED-COUNT` / `WS-SKIPPED-COUNT` | 9(09) | §4.1 |

カウンタは初期値 0。ヘッダが来ても 0 に戻らない（2 回目のヘッダ以降も通算 **[実行確認 E16]**）。

### 4.4 桁あふれ時の挙動 — 上位桁の切捨て **[実行確認 E03–E08]**

どの演算にも `ON SIZE ERROR` がないため、受け側の桁数に収まらない値は **上位桁を黙って切り捨てる**（= 10^整数桁数 の剰余）。RC は 0 のまま、メッセージも出ない。

| ケース | 本来の値 | COBOL が格納した値 | 確認 |
|---|---|---|---|
| `WS-WORK`（整数 15 桁）: 残高 999,999,999,999,999 × 999.9999% / 100 | 9,999,998,999,999,990.000001 | 999,998,999,999,990.000001 → 日次利息 **2,739,723,287,671.20**（本来は 27,397,257,534,246.54） | E03 |
| `WS-WORK`: 残高 200,000,000,000,000 × 999.9999% / 100 | 1,999,999,800,000,000 | 999,999,800,000,000 → 日次利息 **2,739,725,479,452.05**（本来は 5,479,451,506,849.31） | E04 |
| `WS-WORK` 上限ちょうど: 100,000,000,000,000 × 100% / 100 | 100,000,000,000,000 | そのまま（15 桁に収まる）→ 273,972,602,739.72 | E05 |
| `WS-DTL-ACCRUED`（9(15)V9(02)）: 999,999,999,999,999.99 + 10.00 | 1,000,000,000,000,009.99 | **9.99**（`00000000000000999`） | E06 |
| `WS-TOTAL-BALANCE`（9(17)）: 999,999,999,999,999 × 101 件 | 100,999,999,999,999,899 | **999,999,999,999,899**（`00999999999999899`） | E07 |
| `WS-TOTAL-ACCRUED`（9(15)V9(02)）: 999,999,999,999,999.99 × 2 件 | 1,999,999,999,999,999.98 | **999,999,999,999,999.98** | E08 |

- 日次利息 `WS-DAILY-INT` と `WS-TOTAL-DAILY` の整数部も 15 桁だが、`WS-WORK` が 15 桁で切られるので ÷365 の結果はあふれない。
- LOG の編集項目（`Z(16)9` は 17 桁、`Z(14)9.99` は整数 15 桁）は元の項目以上の桁があるので、LOG 編集であふれることはない。
- 移植版でバイト一致を取るなら、`WS-WORK`・未払利息・各合計の格納時に「10^n で剰余を取る」処理が要る。業務的には桁あふれは異常値なので、エラーにするか COBOL に合わせるかは設計メモ（Java 版バッチの設計）で決めること（§8）。

### 4.5 数値項目に数字以外が入った場合 **[実行確認 E17, E18]**

COBOL は数値項目の中身を検査しない。残高が空白の行や、短い行で利率・未払利息が空白で埋まった行も RC=0 で処理され、意味のない値（例: 未払利息 `46861907171726805`、残高合計 `46744073709551600`）が出力される。値は GnuCOBOL の内部実装に依存するので **再現の対象にしない**。移植版では数字以外をエラーとして扱うことを推奨する（§8）。

---

## 5. トレーラ処理

```cobol
MOVE ACCT-REC TO WS-TRL
IF WS-TRL-COUNT NOT = WS-READ-COUNT
    DISPLAY "UBEOD001E TRAILER COUNT MISMATCH IN=" WS-TRL-COUNT " READ=" WS-READ-COUNT
    MOVE 8 TO RETURN-CODE
END-IF
MOVE "T" / WS-READ-COUNT / WS-TOTAL-BALANCE / WS-TOTAL-ACCRUED TO WS-TRL-…
WRITE OUT-REC FROM WS-TRL
```

- 入力トレーラは **件数（2–10 桁）の照合だけ** に使う。残高合計・未払利息合計は読まない。
- 照合の相手は、トレーラを読んだ時点までの D 件数（`WS-READ-COUNT`）。
- 不一致でも処理は止めない。出力トレーラは **再集計値** で書き、RC=8 で終わる **[実行確認 E09]**。
- 出力トレーラの未払利息合計は加算 **後** の値（ゴールデン: 入力 `…00572025` → 出力 `…00574401`）。

---

## 6. 終了コードと DISPLAY メッセージ

### 6.1 終了コード

| RC | 条件 | その後の処理 |
|---|---|---|
| 0 | 正常 | — |
| 8 | トレーラ件数不一致、または未知のレコード区分（空行を含む） | 処理を続け、出力ファイルと LOG をすべて書く。8 は後で 0 に戻らない **[実行確認 E09–E11]** |
| 12 | `ACCOUNTS.DAT` の OPEN エラー（FILE STATUS ≠ `00`。ファイルがなければ `35`） | 直ちに `STOP RUN`。`ACCRUED.DAT`・`UBEOD001.LOG` は作られない（OPEN 前）**[実行確認 E12]** |

- 未知のレコード区分の行は **出力しない**、件数にも入れない **[実行確認 E10]**。
- 出力ファイルの OPEN エラーや WRITE エラーは判定していない（`WS-OUT-STATUS` は定義だけで未使用）。
- `run.sh` は `set -e` 付きなので、RC が 0 以外だと `./ubeod001` の行で終了し、`RC=…` の表示とゴールデン比較が行われない（`run.sh` の終了コードは RC と同じ 8）**[実行確認 E25]**。

### 6.2 DISPLAY メッセージ（すべて stdout）**[実行確認 E09, E10, E12, E24]**

数値は `9(09)` のまま出すので 9 桁ゼロ埋め。

| 種別 | 文言（`<…>` は値） | 例 |
|---|---|---|
| OPEN エラー | `UBEOD001E ACCOUNTS.DAT OPEN ERROR <FILE STATUS 2 桁>` | `UBEOD001E ACCOUNTS.DAT OPEN ERROR 35` |
| 未知区分 | `UBEOD001E UNKNOWN RECORD KIND: <区分 1 桁>` | `UBEOD001E UNKNOWN RECORD KIND: X`（空行では末尾が空白 1 個） |
| 件数不一致 | `UBEOD001E TRAILER COUNT MISMATCH IN=<入力件数 9 桁> READ=<読込件数 9 桁>` | `UBEOD001E TRAILER COUNT MISMATCH IN=000000005 READ=000000001` |
| 完了 | `UBEOD001I COMPLETED. READ=<9 桁> ACCRUED=<9 桁> SKIPPED=<9 桁>` | `UBEOD001I COMPLETED. READ=000000015 ACCRUED=000000011 SKIPPED=000000004` |

完了メッセージは RC=8 でも出る。RC=12 では出ない。

---

## 7. 既存 Java との対応と相違点

### 7.1 対応表

| COBOL | 既存 Java | 備考 |
|---|---|---|
| 利息対象判定（§4.1） | `InterestService.bearsInterest(Account)`: `status == ACTIVE && accountType != CURRENT` | 判定内容は同じ。ただし Java は enum 前提（§7.2-5） |
| 日次利息（§4.2） | `InterestService.dailyInterest(balance, rate)`: `÷100`（scale 10, `ROUND_DOWN`）→ `÷365`（scale 2, `ROUND_DOWN`） | 通常範囲では 1 銭まで一致（`CobolParityTest` で 15 件確認） |
| D レコード編集 | `BatchFileService.formatRecord(Account)` / `numeric(value, digits, scale)` | 桁位置・ゼロ埋めは同じ |
| ACCOUNTS.DAT 作成 | `BatchFileService.exportAccounts`（`GET /api/batch/accounts-file`） | COBOL の入力を作る側 |
| ACCRUED.DAT 取込 | `BatchFileService.importAccrued`（`POST /api/batch/accrued-file`） | COBOL の出力を読む側。D の 36–52 桁（未払利息）だけ使う |
| 日次積数（DB 上） | `InterestService.accrueDaily()`（`EndOfDayJob` / `POST /api/batch/eod`） | ファイルではなく DB の ACTIVE 口座を直接更新。LOG・トレーラなし |
| 文字コード | `BatchFileService.HOST_CHARSET` = MS932 | COBOL は変換なし（§3.3） |
| ゴールデン | `src/test/resources/golden/ACCOUNTS.DAT`・`ACCRUED_COBOL.DAT` | `batch/cobol/data/ACCOUNTS.DAT`・`expected/ACCRUED.DAT` と同一内容（`diff` で確認） |

### 7.2 相違点

1. **末尾空白**: `exportAccounts` はヘッダ・トレーラを `StringUtils.rightPad(…, 52)` で 52 桁に **空白で埋める**。COBOL の出力は埋めない（§3.1）。COBOL の入力としてはどちらでも同じ結果になる（§3.2）。`importAccrued` も `substring(1, 9)` / `substring(1, 10)` しか見ないので両方読める。移植版の出力は **埋めない側（COBOL）に合わせる**。
2. **桁あふれ**: `numeric()` は桁数を超えると `BankingException("UB-9004", "桁あふれ: …")` を投げる。COBOL は上位桁を黙って切り捨てる（§4.4）。また `dailyInterest()` には `WS-WORK` の 15 桁制限がないので、E03 の入力では Java = 27,397,257,534,246.54、COBOL = 2,739,723,287,671.20 と **例外なしで値が食い違う**（付録 B）。
3. **未知のレコード区分**: `importAccrued` は `UB-9002` で即中断。COBOL は飛ばして続行し RC=8。
4. **空行**: `importAccrued` は `line.trim().isEmpty()` を読み飛ばす。COBOL は未知区分扱いで RC=8（E11）。
5. **種目・状態コード**: Java は `AccountType` / `AccountStatus` の enum に変換するので、全銀コードにない種目（`3`、空白）や未定義の状態は変換時にエラーになる。COBOL は文字比較だけなので、種目 `3` も利息対象、未定義の状態は対象外として処理を続ける（E22）。
6. **トレーラ件数照合**: `importAccrued` はファイル末尾で `宣言件数 ≠ updated + skipped` なら `UB-9003` で中断。COBOL はトレーラを読んだ時点の D 件数と比べ、続行して RC=8。
7. **短いレコード**: `importAccrued` は 52 文字未満の D 行を `UB-9001` で拒否。COBOL は空白で埋めて処理する（数値項目が空白なら意味のない値、§4.5）。
8. **長いレコード**: Java は 53 文字目以降を見ないだけ。COBOL は捨てる。結果は同じ。
9. **桁の数え方**: Java は MS932 で文字列にしてから `substring` するので文字単位、COBOL はバイト単位（§3.3）。
10. **LOG と終了コード**: 既存 Java には `UBEOD001.LOG` に当たる出力も RC もない（`accrueDaily` は件数を `log.info` に出すだけ）。
11. **残高 0 以下**: `dailyInterest` は `balance.signum() <= 0` で 0.00 を返す。COBOL の残高は符号なしで負にならず、0 なら計算結果も 0.00 なので一致する。
12. **コメントの古い参照**: `InterestService` の Javadoc は `cobol/INTCALC.cbl` を参照しているが、実在するのは `batch/cobol/UBEOD001.cbl`。

---

## 8. 移植時に決めること（本書では決めない）

本書は現行の挙動を記録するだけ。次の点は Java 版バッチの設計メモで決めること。

| # | 論点 | 現行 COBOL | 選択肢 |
|---|---|---|---|
| 1 | 桁あふれ（§4.4） | 上位桁を黙って切捨て、RC=0 | COBOL に合わせて剰余を取る / エラー（RC=8 や 12）にする |
| 2 | 数値項目に数字以外（§4.5） | 意味のない値で処理、RC=0 | エラーにする（推奨。再現は不可能） |
| 3 | ASCII 以外のバイト（§3.3） | バイト単位で処理 | バイト単位で処理する / エラーにする |
| 4 | 出力ファイルの OPEN・WRITE エラー | 判定なし | RC=12 などで止める |
| 5 | ファイル名・作業ディレクトリ | カレントディレクトリ固定 | 引数で指定できるようにするか |

バイト一致が必要な範囲（ゴールデン）: `ACCRUED.DAT`、`UBEOD001.LOG`、stdout のメッセージ、終了コード。

---

## 9. 実行確認の記録

### 9.1 E01: 現行ゴールデン（`run.sh`）

```console
$ cd batch/cobol && ./run.sh; echo "exit=$?"
UBEOD001I COMPLETED. READ=000000015 ACCRUED=000000011 SKIPPED=000000004
RC=0
GOLDEN OK: ACCRUED.DAT matches expected/ACCRUED.DAT
exit=0

$ cd work && od -c ACCRUED.DAT | head -2
0000000   H   2   0   1   8   0   3   1   5  \n   D   0   0   1   1   0
0000020   0   0   0   0   1   1   A   0   0   0   0   0   0   0   0   1
$ od -c ACCRUED.DAT | tail -3
0001500   0   0   0   0   0   0   0   0   0   0   0   5   7   4   4   0
0001520   1  \n
0001522
$ wc -c ACCRUED.DAT ../expected/ACCRUED.DAT UBEOD001.LOG
 850 ACCRUED.DAT
 850 ../expected/ACCRUED.DAT
 322 UBEOD001.LOG
$ cmp UBEOD001.LOG ../expected/UBEOD001.LOG && echo LOG_SAME
LOG_SAME
$ awk '{print length($0)}' UBEOD001.LOG | tr '\n' ' '
31 47 47 47 47 48 48
$ grep -c $'\r' ACCRUED.DAT
0
```

入力と期待値（`cat -A`。`$` が行末）:

```console
$ cat -A data/ACCOUNTS.DAT | sed -n '1p;2p;$p'
H20180315                                           $
D00110000011A000000001250000000001000000000000001234$
T0000000150000000010247568000000000000572025        $
$ cat -A expected/ACCRUED.DAT | sed -n '1p;2p;$p'
H20180315$
D00110000011A000000001250000000001000000000000001237$
T0000000150000000010247568000000000000574401$
$ cat -A expected/UBEOD001.LOG
UBEOD001 NICHIJI RISOKU SEKISUU$
SHORI-BI (YYYYMMDD)                    20180315$
YOMIKOMI KENSUU                               15$
RISOKU KEISAN KENSUU                          11$
TAISHOUGAI KENSUU                             4$
TOUJITSU RISOKU GOUKEI (YEN)               23.76$
MIBARAI RISOKU GOUKEI (YEN)              5744.01$
```

### 9.2 E02: 既存 JUnit（出力は抜粋）

```console
$ JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 mvn -B test
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 - in jp.usagi.bank.service.CobolParityTest
Tests run: 39, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### 9.3 E03–E24: 境界・異常系（付録 A のスクリプト）

`bash exp.sh` の出力から該当部分を抜粋する（`cat -A` 表示。`$` が行末）。LOG は関係する行だけ載せる。

#### E03 `WS-WORK` 桁あふれ（残高 999999999999999、利率 999.9999）

```
UBEOD001I COMPLETED. READ=000000001 ACCRUED=000000001 SKIPPED=000000000
RC=0
--- ACCRUED.DAT
H20180315$
D00199999991A999999999999999999999900273972328767120$
T0000000010099999999999999900273972328767120$
--- UBEOD001.LOG
TOUJITSU RISOKU GOUKEI (YEN)    2739723287671.20$
MIBARAI RISOKU GOUKEI (YEN)     2739723287671.20$
```

検算（Python `decimal`）:

```
WORK(正確)=9999998999999990.000001  WORK(15桁切捨)=999998999999990.000001
daily(COBOL)=2739723287671.20  daily(切捨てなし)=27397257534246.54
```

#### E04 `WS-WORK` 桁あふれ（残高 200000000000000、利率 999.9999）

```
RC=0
D00199999991A200000000000000999999900273972547945205$
T0000000010020000000000000000273972547945205$
TOUJITSU RISOKU GOUKEI (YEN)    2739725479452.05$
```

検算: WORK = 1999999800000000 → 15 桁に切って 999999800000000 → ÷365 = 2739725479452.05（切捨てなしなら 5479451506849.31）。

#### E05 `WS-WORK` 上限ちょうど（残高 100000000000000、利率 100.0000）

```
RC=0
D00199999991A100000000000000100000000027397260273972$
TOUJITSU RISOKU GOUKEI (YEN)     273972602739.72$
```

#### E06 未払利息 9(15)V9(2) 桁あふれ（999999999999999.99 + 10.00）

```
RC=0
--- ACCRUED.DAT
D00199999991A000000000365000001000000000000000000999$
T0000000010000000000036500000000000000000999$
--- UBEOD001.LOG
TOUJITSU RISOKU GOUKEI (YEN)               10.00$
MIBARAI RISOKU GOUKEI (YEN)                 9.99$
```

#### E07 残高合計 9(17) 桁あふれ（999999999999999 × 101 件、状態 C）

```
UBEOD001I COMPLETED. READ=000000101 ACCRUED=000000000 SKIPPED=000000101
RC=0
T0000001010099999999999989900000000000000000$
```

101 × 999999999999999 = 100999999999999899 → 下 17 桁 `00999999999999899`。

#### E08 未払利息合計 9(15)V9(2) 桁あふれ（999999999999999.99 × 2 件）

```
RC=0
T0000000020000000000000000099999999999999998$
MIBARAI RISOKU GOUKEI (YEN)   999999999999999.98$
```

#### E09 トレーラ件数不一致

```
UBEOD001E TRAILER COUNT MISMATCH IN=000000005 READ=000000001
UBEOD001I COMPLETED. READ=000000001 ACCRUED=000000001 SKIPPED=000000000
RC=8
--- ACCRUED.DAT
H20180315$
D00110000011A000000001250000000001000000000000001237$
T0000000010000000000125000000000000000001237$
```

#### E10 未知のレコード区分 `X`

```
UBEOD001E UNKNOWN RECORD KIND: X
UBEOD001I COMPLETED. READ=000000001 ACCRUED=000000001 SKIPPED=000000000
RC=8
--- ACCRUED.DAT
H20180315$
D00110000011A000000001250000000001000000000000001237$
T0000000010000000000125000000000000000001237$
```

（`X` の行は出力されていない）

#### E11 空行

```
UBEOD001E UNKNOWN RECORD KIND:  
UBEOD001I COMPLETED. READ=000000001 ACCRUED=000000001 SKIPPED=000000000
RC=8
```

#### E12 入力ファイルなし

```
UBEOD001E ACCOUNTS.DAT OPEN ERROR 35
RC=12
--- ACCRUED.DAT (作成されない)
--- UBEOD001.LOG (作成されない)
```

#### E13 ヘッダなし

```
RC=0
--- ACCRUED.DAT
D00110000011A000000001250000000001000000000000001237$
T0000000010000000000125000000000000000001237$
--- UBEOD001.LOG
SHORI-BI (YYYYMMDD)                           0$
```

#### E14 空ファイル

```
UBEOD001I COMPLETED. READ=000000000 ACCRUED=000000000 SKIPPED=000000000
RC=0
--- ACCRUED.DAT
--- UBEOD001.LOG
UBEOD001 NICHIJI RISOKU SEKISUU$
SHORI-BI (YYYYMMDD)                           0$
YOMIKOMI KENSUU                               0$
RISOKU KEISAN KENSUU                          0$
TAISHOUGAI KENSUU                             0$
TOUJITSU RISOKU GOUKEI (YEN)                0.00$
MIBARAI RISOKU GOUKEI (YEN)                 0.00$
```

#### E15 トレーラなし

```
RC=0
--- ACCRUED.DAT
H20180315$
D00110000011A000000001250000000001000000000000001237$
```

#### E16 トレーラの後のレコード・2 個目のヘッダ

```
UBEOD001I COMPLETED. READ=000000002 ACCRUED=000000002 SKIPPED=000000000
RC=0
--- ACCRUED.DAT
H20180315$
D00110000011A000000001250000000001000000000000001237$
T0000000010000000000125000000000000000001237$
H20990101$
D00110000011A000000001250000000001000000000000001237$
--- UBEOD001.LOG
SHORI-BI (YYYYMMDD)                    20990101$
YOMIKOMI KENSUU                               2$
```

#### E17 52 桁超（末尾に `XYZ`）/ 52 桁未満（29 桁）

```
RC=0
--- ACCRUED.DAT
H20180315$
D00110000011A000000001250000000001000000000000001237$
D00110000011A0000000012500000      46861907171726805$
T0000000020000000000250000046861907171728042$
```

1 行目: `XYZ` は捨てられ、通常どおり計算。2 行目: 30–52 桁が空白で埋まり、利率・未払利息が数字以外になって意味のない値が出る（§4.5）。

#### E18 残高が空白（数字以外）

```
RC=0
D00110000011A               001000000128065955368757$
T0000000014674407370955160000128065955368757$
```

#### E19 CRLF 入力 / E20 最終行に改行なし

どちらも LF 入力と同じ出力（RC=0）。E20 の出力末尾:

```
0000140   0   0   0   0   0   0   0   1   2   3   7  \n
0000154
```

#### E21 ヘッダ・トレーラの FILLER 引き継ぎ

入力: `H20180315 FILLERXX` / トレーラの 45–52 桁に `TRLFILL`

```
--- ACCRUED.DAT
H20180315 FILLERXX$
D00110000011A000000001250000000001000000000000001237$
T0000000010000000000125000000000000000001237TRLFILL$
```

#### E22 種目・状態の判定

入力の種目/状態: `2/A`, `4/A`, `9/A`, `3/A`, `空白/A`, `1/F`, `1/空白`

```
UBEOD001I COMPLETED. READ=000000007 ACCRUED=000000004 SKIPPED=000000003
--- ACCRUED.DAT
D00110000012A000000001250000000001000000000000001234$   ← 当座: 対象外
D00110000014A000000001250000000001000000000000001237$   ← 貯蓄: 対象
D00110000019A000000001250000000001000000000000001237$   ← 定期: 対象
D00110000013A000000001250000000001000000000000001237$   ← 種目 3（未定義）: 対象
D0011000001 A000000001250000000001000000000000001237$   ← 種目 空白: 対象
D00110000011F000000001250000000001000000000000001234$   ← 凍結: 対象外
D00110000011 000000001250000000001000000000000001234$   ← 状態 空白: 対象外
```

（`←` 以降は説明のために付けた注記）

#### E23 MS932 の 2 バイト文字（`ア` = 0x83 0x41）を口座番号に含む

```
RC=0
--- ACCRUED.DAT
D001M-^CA999991A000000001250000000001000000000000001237$
バイト長 (入力/出力):
9 52 10
9 52 44
```

52 バイトとして正しく桁を切り、利息も計算されている。

#### E24 DISPLAY の出力先

```
stdout:
UBEOD001E TRAILER COUNT MISMATCH IN=000000005 READ=000000001
UBEOD001I COMPLETED. READ=000000001 ACCRUED=000000001 SKIPPED=000000000
stderr (空なら stderr 出力なし):
```

#### E25 `run.sh` に RC=8 になる入力を渡す

```console
$ cd batch/cobol && ./run.sh /tmp/ubeod-exp/E09/ACCOUNTS.DAT; echo "run.sh exit=$?"
UBEOD001E TRAILER COUNT MISMATCH IN=000000005 READ=000000001
UBEOD001I COMPLETED. READ=000000001 ACCRUED=000000001 SKIPPED=000000000
run.sh exit=8
```

`RC=8` の行もゴールデン比較も出ない（`set -e` で終了）。

---

## 付録 A: 挙動確認スクリプト `exp.sh`

リポジトリには追加していない。再確認するときはこの内容を `/tmp/ubeod-exp/exp.sh` などに保存して `bash exp.sh <リポジトリのルート>` で実行する（GnuCOBOL `cobc` が必要）。

```bash
#!/bin/bash
# UBEOD001 挙動確認スクリプト (GnuCOBOL 3.1.2). 使い方: bash exp.sh [REPO_ROOT]
REPO=${1:-$HOME/repos/Usagi-Bank}
W=/tmp/ubeod-exp; mkdir -p $W; cd $W
cobc -x -o $W/ubeod001 $REPO/batch/cobol/UBEOD001.cbl || exit 1
H=H20180315
G1=D00110000011A000000001250000000001000000000000001234
run() {   # run NAME  (標準入力 = ACCOUNTS.DAT)
  rm -rf $W/$1; mkdir -p $W/$1; cd $W/$1
  [ "$2" = nofile ] || cat > ACCOUNTS.DAT
  $W/ubeod001; echo "RC=$?"
  for f in ACCRUED.DAT UBEOD001.LOG; do
    if [ -f $f ]; then echo "--- $f"; cat -A $f; else echo "--- $f (作成されない)"; fi
  done
  cd $W
}
echo "### E03 WS-WORK 桁あふれ (残高 999999999999999, 利率 999.9999)"
printf '%s\n' $H D00199999991A999999999999999999999900000000000000000 T000000001 | run E03
echo "### E04 WS-WORK 桁あふれ (残高 200000000000000, 利率 999.9999)"
printf '%s\n' $H D00199999991A200000000000000999999900000000000000000 T000000001 | run E04
echo "### E05 WS-WORK 上限ちょうど (残高 100000000000000, 利率 100.0000)"
printf '%s\n' $H D00199999991A100000000000000100000000000000000000000 T000000001 | run E05
echo "### E06 未払利息 9(15)V9(2) 桁あふれ (999999999999999.99 + 10.00)"
printf '%s\n' $H D00199999991A000000000365000001000099999999999999999 T000000001 | run E06
echo "### E07 残高合計 9(17) 桁あふれ (999999999999999 x 101 件, 状態 C)"
{ echo $H; for i in $(seq 101); do echo D00199999991C999999999999999001000000000000000000000; done; echo T000000101; } | run E07 | grep -v '^D'
echo "### E08 未払利息合計 9(15)V9(2) 桁あふれ (999999999999999.99 x 2 件)"
printf '%s\n' $H D00199999991C000000000000000001000099999999999999999 D00199999991C000000000000000001000099999999999999999 T000000002 | run E08
echo "### E09 トレーラ件数不一致"
printf '%s\n' $H $G1 T000000005 | run E09
echo "### E10 未知のレコード区分 X"
printf '%s\n' $H X${G1:1} $G1 T000000001 | run E10
echo "### E11 空行"
printf '%s\n' $H '' $G1 T000000001 | run E11
echo "### E12 入力ファイルなし"
run E12 nofile
echo "### E13 ヘッダなし"
printf '%s\n' $G1 T000000001 | run E13
echo "### E14 空ファイル"
printf '' | run E14
echo "### E15 トレーラなし"
printf '%s\n' $H $G1 | run E15
echo "### E16 トレーラ後のレコード・2 個目のヘッダ"
printf '%s\n' $H $G1 T000000001 H20990101 $G1 | run E16
echo "### E17 52 桁超 (末尾に XYZ) / 52 桁未満 (29 桁)"
printf '%s\n' $H ${G1}XYZ D00110000011A0000000012500000 T000000002 | run E17
echo "### E18 残高が空白 (非数値)"
printf '%s\n' $H 'D00110000011A               0010000000000000000001234' T000000001 | run E18
echo "### E19 CRLF 入力"
printf '%s\r\n' $H $G1 T000000001 | run E19
echo "### E20 最終行に改行なし"
printf '%s\n%s\n%s' $H $G1 T000000001 | run E20; od -c E20/ACCRUED.DAT | tail -2
echo "### E21 ヘッダ/トレーラの FILLER 引き継ぎ"
printf '%s\n' 'H20180315 FILLERXX' $G1 'T000000001                                  TRLFILL' | run E21
echo "### E22 種目・状態の判定 (種目 2/4/9/3/空白, 状態 F/空白)"
printf '%s\n' $H D00110000012A000000001250000000001000000000000001234 D00110000014A000000001250000000001000000000000001234 \
  D00110000019A000000001250000000001000000000000001234 D00110000013A000000001250000000001000000000000001234 \
  'D0011000001 A000000001250000000001000000000000001234' D00110000011F000000001250000000001000000000000001234 \
  'D00110000011 000000001250000000001000000000000001234' T000000007 | run E22
echo "### E23 MS932 2 バイト文字 (ア = 0x83 0x41) を口座番号に含む"
{ printf '%s\nD001\x83\x41999991A000000001250000000001000000000000001234\nT000000001\n' $H; } | run E23
echo "バイト長 (入力/出力):"; LC_ALL=C awk '{printf "%d ", length($0)}' E23/ACCOUNTS.DAT; echo; LC_ALL=C awk '{printf "%d ", length($0)}' E23/ACCRUED.DAT; echo
echo "### E24 DISPLAY の出力先"
(cd E09 && $W/ubeod001 >../E24.out 2>../E24.err); echo "stdout:"; cat E24.out; echo "stderr (空なら stderr 出力なし):"; cat E24.err
```

## 付録 B: 既存 Java の挙動確認（`Probe.java`）

リポジトリには追加していない。`mvn test` 後の `target/classes` と依存 jar を classpath にして JDK 8 で実行した。

```java
package jp.usagi.bank.service;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import org.apache.commons.io.IOUtils;

public class Probe {
    public static void main(String[] a) throws Exception {
        String[][] cases = {{"999999999999999", "999.9999"}, {"200000000000000", "999.9999"},
                {"100000000000000", "100.0000"}, {"1250000", "0.0010"}, {"1", "0.0001"}};
        for (String[] c : cases) {
            BigDecimal d = InterestService.dailyInterest(new BigDecimal(c[0]), new BigDecimal(c[1]));
            System.out.println("dailyInterest(" + c[0] + ", " + c[1] + ") = " + d);
        }
        BigDecimal[] vals = {new BigDecimal("27397257534246.54"), new BigDecimal("1000000000000009.99"),
                new BigDecimal("100999999999999899")};
        int[][] fmt = {{17, 2}, {17, 2}, {17, 0}};
        for (int i = 0; i < vals.length; i++) {
            try {
                System.out.println("numeric(" + vals[i] + "," + fmt[i][0] + "," + fmt[i][1] + ") = "
                        + BatchFileService.numeric(vals[i], fmt[i][0], fmt[i][1]));
            } catch (BankingException e) {
                System.out.println("numeric(" + vals[i] + "," + fmt[i][0] + "," + fmt[i][1] + ") -> "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        byte[] ms = new byte[] {'D', '0', '0', '1', (byte) 0x83, 0x41, '9', '9', '9', '9', '1', 'A'};
        List<String> l = IOUtils.readLines(new ByteArrayInputStream(ms), BatchFileService.HOST_CHARSET);
        System.out.println("MS932 12 bytes -> Java chars = " + l.get(0).length() + " : " + l.get(0));
    }
}
```

```console
$ mvn -B -q dependency:build-classpath -Dmdep.outputFile=/tmp/ubeod-exp/cp.txt
$ CP=$(cat /tmp/ubeod-exp/cp.txt):target/classes
$ mkdir -p out && /usr/lib/jvm/java-8-openjdk-amd64/bin/javac -encoding UTF-8 -cp "$CP" -d out Probe.java
$ /usr/lib/jvm/java-8-openjdk-amd64/bin/java -cp "out:$CP" jp.usagi.bank.service.Probe
dailyInterest(999999999999999, 999.9999) = 27397257534246.54
dailyInterest(200000000000000, 999.9999) = 5479451506849.31
dailyInterest(100000000000000, 100.0000) = 273972602739.72
dailyInterest(1250000, 0.0010) = 0.03
dailyInterest(1, 0.0001) = 0.00
numeric(27397257534246.54,17,2) = 02739725753424654
numeric(1000000000000009.99,17,2) -> BankingException: 桁あふれ: 1000000000000009.99
numeric(100999999999999899,17,0) -> BankingException: 桁あふれ: 100999999999999899
MS932 12 bytes -> Java chars = 11 : D001ア99991A
```

- 1 行目と E03 を比べると、同じ入力で Java（27397257534246.54）と COBOL（2739723287671.20）が食い違い、Java の `numeric` もこの値は 17 桁に収まるので例外にならない。
- 未払利息の桁あふれ（E06 相当）と残高合計の桁あふれ（E07 相当）は、Java では `UB-9004` の例外になる。
