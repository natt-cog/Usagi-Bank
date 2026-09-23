       IDENTIFICATION DIVISION.
       PROGRAM-ID. UBEOD001.
      ******************************************************************
      * うさぎ銀行 勘定系 日次バッチ                                     *
      * UBEOD001 : 預金口座 日次利息積数計算                             *
      *                                                                *
      * 入力 : ACCOUNTS.DAT  (固定長 52 桁, 口座マスタ抽出ファイル)      *
      * 出力 : ACCRUED.DAT   (固定長 52 桁, 未払利息更新ファイル)        *
      *        UBEOD001.LOG  (処理件数レポート)                          *
      *                                                                *
      * 処理 : 有効 (A) かつ 当座 (2) 以外の口座について                *
      *        日次利息 = 残高 × 年利(%) ÷ 100 ÷ 365 (銭未満切捨て)      *
      *        を未払利息に加算する.                                     *
      *                                                                *
      * 変更履歴                                                        *
      *   1998/03/02 初版                            (第一システム部)   *
      *   2007/10/01 利率桁数拡張 (5→7)              (田中)             *
      *   2014/04/01 消費税改定対応 (該当なし・確認のみ) (鈴木)          *
      ******************************************************************
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT ACCT-FILE ASSIGN TO "ACCOUNTS.DAT"
               ORGANIZATION IS LINE SEQUENTIAL
               FILE STATUS IS WS-ACCT-STATUS.
           SELECT OUT-FILE ASSIGN TO "ACCRUED.DAT"
               ORGANIZATION IS LINE SEQUENTIAL
               FILE STATUS IS WS-OUT-STATUS.
           SELECT LOG-FILE ASSIGN TO "UBEOD001.LOG"
               ORGANIZATION IS LINE SEQUENTIAL.

       DATA DIVISION.
       FILE SECTION.
       FD  ACCT-FILE.
       01  ACCT-REC.
           05  ACCT-KIND               PIC X(01).
           05  ACCT-BODY               PIC X(51).

       FD  OUT-FILE.
       01  OUT-REC                     PIC X(52).

       FD  LOG-FILE.
       01  LOG-REC                     PIC X(80).

       WORKING-STORAGE SECTION.
       01  WS-ACCT-STATUS              PIC X(02).
       01  WS-OUT-STATUS               PIC X(02).
       01  WS-EOF                      PIC X(01) VALUE "N".

      *    ヘッダレコード
       01  WS-HDR.
           05  WS-HDR-KIND             PIC X(01).
           05  WS-HDR-DATE             PIC 9(08).
           05  FILLER                  PIC X(43).

      *    データレコード (52 桁)
       01  WS-DTL.
           05  WS-DTL-KIND             PIC X(01).
           05  WS-DTL-BRANCH           PIC X(03).
           05  WS-DTL-ACCT-NO          PIC X(07).
           05  WS-DTL-TYPE             PIC X(01).
           05  WS-DTL-STATUS           PIC X(01).
           05  WS-DTL-BALANCE          PIC 9(15).
           05  WS-DTL-RATE             PIC 9(03)V9(04).
           05  WS-DTL-ACCRUED          PIC 9(15)V9(02).

      *    トレーラレコード
       01  WS-TRL.
           05  WS-TRL-KIND             PIC X(01).
           05  WS-TRL-COUNT            PIC 9(09).
           05  WS-TRL-BALANCE          PIC 9(17).
           05  WS-TRL-ACCRUED          PIC 9(15)V9(02).
           05  FILLER                  PIC X(08).

      *    計算用
       01  WS-CALC.
           05  WS-DAILY-INT            PIC 9(15)V9(02).
           05  WS-WORK                 PIC 9(15)V9(10).
           05  WS-DAYS-IN-YEAR         PIC 9(03) VALUE 365.

      *    集計
       01  WS-COUNTERS.
           05  WS-READ-COUNT           PIC 9(09) VALUE ZERO.
           05  WS-ACCRUED-COUNT        PIC 9(09) VALUE ZERO.
           05  WS-SKIPPED-COUNT        PIC 9(09) VALUE ZERO.
           05  WS-TOTAL-BALANCE        PIC 9(17) VALUE ZERO.
           05  WS-TOTAL-ACCRUED        PIC 9(15)V9(02) VALUE ZERO.
           05  WS-TOTAL-DAILY          PIC 9(15)V9(02) VALUE ZERO.

       01  WS-LOG-LINE.
           05  WS-LOG-LABEL            PIC X(30).
           05  WS-LOG-VALUE            PIC Z(16)9.
           05  FILLER                  PIC X(33) VALUE SPACES.

       01  WS-LOG-AMT-LINE.
           05  WS-LOG-AMT-LABEL        PIC X(30).
           05  WS-LOG-AMT-VALUE        PIC Z(14)9.99.
           05  FILLER                  PIC X(32) VALUE SPACES.

       PROCEDURE DIVISION.
       0000-MAIN.
           PERFORM 1000-OPEN
           PERFORM 2000-PROCESS UNTIL WS-EOF = "Y"
           PERFORM 3000-CLOSE
           STOP RUN.

       1000-OPEN.
           OPEN INPUT  ACCT-FILE
           IF WS-ACCT-STATUS NOT = "00"
               DISPLAY "UBEOD001E ACCOUNTS.DAT OPEN ERROR "
                       WS-ACCT-STATUS
               MOVE 12 TO RETURN-CODE
               STOP RUN
           END-IF
           OPEN OUTPUT OUT-FILE
           OPEN OUTPUT LOG-FILE
           PERFORM 2100-READ.

       2000-PROCESS.
           EVALUATE ACCT-KIND
               WHEN "H"
                   PERFORM 2200-HEADER
               WHEN "D"
                   PERFORM 2300-DETAIL
               WHEN "T"
                   PERFORM 2400-TRAILER
               WHEN OTHER
                   DISPLAY "UBEOD001E UNKNOWN RECORD KIND: " ACCT-KIND
                   MOVE 8 TO RETURN-CODE
           END-EVALUATE
           PERFORM 2100-READ.

       2100-READ.
           READ ACCT-FILE
               AT END MOVE "Y" TO WS-EOF
           END-READ.

       2200-HEADER.
           MOVE ACCT-REC TO WS-HDR
           WRITE OUT-REC FROM ACCT-REC.

       2300-DETAIL.
           MOVE ACCT-REC TO WS-DTL
           ADD 1 TO WS-READ-COUNT
           ADD WS-DTL-BALANCE TO WS-TOTAL-BALANCE

      *    利息対象判定: 有効口座かつ当座以外
           IF WS-DTL-STATUS = "A" AND WS-DTL-TYPE NOT = "2"
      *        日次利息 = 残高 × 利率 ÷ 100 ÷ 365 (銭未満切捨て)
               COMPUTE WS-WORK = WS-DTL-BALANCE * WS-DTL-RATE / 100
               COMPUTE WS-DAILY-INT ROUNDED MODE TRUNCATION
                   = WS-WORK / WS-DAYS-IN-YEAR
               ADD WS-DAILY-INT TO WS-DTL-ACCRUED
               ADD WS-DAILY-INT TO WS-TOTAL-DAILY
               ADD 1 TO WS-ACCRUED-COUNT
           ELSE
               ADD 1 TO WS-SKIPPED-COUNT
           END-IF

           ADD WS-DTL-ACCRUED TO WS-TOTAL-ACCRUED
           WRITE OUT-REC FROM WS-DTL.

       2400-TRAILER.
      *    トレーラは再集計した値で出力 (入力トレーラは件数照合のみ)
           MOVE ACCT-REC TO WS-TRL
           IF WS-TRL-COUNT NOT = WS-READ-COUNT
               DISPLAY "UBEOD001E TRAILER COUNT MISMATCH IN="
                       WS-TRL-COUNT " READ=" WS-READ-COUNT
               MOVE 8 TO RETURN-CODE
           END-IF
           MOVE "T"               TO WS-TRL-KIND
           MOVE WS-READ-COUNT     TO WS-TRL-COUNT
           MOVE WS-TOTAL-BALANCE  TO WS-TRL-BALANCE
           MOVE WS-TOTAL-ACCRUED  TO WS-TRL-ACCRUED
           WRITE OUT-REC FROM WS-TRL.

       3000-CLOSE.
           MOVE "UBEOD001 NICHIJI RISOKU SEKISUU  " TO LOG-REC
           WRITE LOG-REC
           MOVE "SHORI-BI (YYYYMMDD)           " TO WS-LOG-LABEL
           MOVE WS-HDR-DATE TO WS-LOG-VALUE
           WRITE LOG-REC FROM WS-LOG-LINE
           MOVE "YOMIKOMI KENSUU               " TO WS-LOG-LABEL
           MOVE WS-READ-COUNT TO WS-LOG-VALUE
           WRITE LOG-REC FROM WS-LOG-LINE
           MOVE "RISOKU KEISAN KENSUU          " TO WS-LOG-LABEL
           MOVE WS-ACCRUED-COUNT TO WS-LOG-VALUE
           WRITE LOG-REC FROM WS-LOG-LINE
           MOVE "TAISHOUGAI KENSUU             " TO WS-LOG-LABEL
           MOVE WS-SKIPPED-COUNT TO WS-LOG-VALUE
           WRITE LOG-REC FROM WS-LOG-LINE
           MOVE "TOUJITSU RISOKU GOUKEI (YEN)  " TO WS-LOG-AMT-LABEL
           MOVE WS-TOTAL-DAILY TO WS-LOG-AMT-VALUE
           WRITE LOG-REC FROM WS-LOG-AMT-LINE
           MOVE "MIBARAI RISOKU GOUKEI (YEN)   " TO WS-LOG-AMT-LABEL
           MOVE WS-TOTAL-ACCRUED TO WS-LOG-AMT-VALUE
           WRITE LOG-REC FROM WS-LOG-AMT-LINE
           CLOSE ACCT-FILE OUT-FILE LOG-FILE
           DISPLAY "UBEOD001I COMPLETED. READ=" WS-READ-COUNT
                   " ACCRUED=" WS-ACCRUED-COUNT
                   " SKIPPED=" WS-SKIPPED-COUNT.
