package jp.usagi.bank.service

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.math.BigDecimal
import java.nio.charset.Charset
import jp.usagi.bank.domain.Account
import jp.usagi.bank.domain.AccountStatus
import jp.usagi.bank.domain.AccountType
import jp.usagi.bank.repository.AccountRepository
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * ホスト連携ファイル (固定長) の入出力.
 *
 * <pre>
 * ACCOUNTS.DAT / ACCRUED.DAT レコードレイアウト (52バイト + LF, MS932):
 *   01     レコード区分   H=ヘッダ D=データ T=トレーラ
 *   02-04  店番           X(3)
 *   05-11  口座番号       X(7)
 *   12     預金種目       X(1)  全銀コード
 *   13     口座状態       X(1)  A=有効 F=凍結 D=休眠 C=解約
 *   14-28  残高           9(15)
 *   29-35  年利           9(3)V9(4)
 *   36-52  未払利息       9(15)V9(2)
 *
 * ヘッダ  : 'H' + 処理日 YYYYMMDD + FILLER
 * トレーラ: 'T' + 件数 9(9) + 残高合計 9(17) + 未払利息合計 9(17)
 * </pre>
 */
@Service
class BatchFileService(
    private val accountRepository: AccountRepository,
    private val businessDateService: BusinessDateService,
) {
    @Transactional(readOnly = true)
    @Throws(IOException::class)
    fun exportAccounts(out: OutputStream): Int {
        val accounts: List<Account> = accountRepository.findAll().sortedBy { it.displayNo }
        val w: Writer = OutputStreamWriter(out, HOST_CHARSET)
        val today = businessDateService.today()
        w.write(StringUtils.rightPad("H" + YYYYMMDD.print(today), RECORD_LENGTH))
        w.write("\n")
        var balanceTotal = BigDecimal.ZERO
        var accruedTotal = BigDecimal.ZERO
        for (a in accounts) {
            w.write(formatRecord(a))
            w.write("\n")
            balanceTotal = balanceTotal.add(a.balance)
            accruedTotal = accruedTotal.add(a.accruedInterest)
        }
        w.write(
            StringUtils.rightPad(
                "T" + StringUtils.leftPad(accounts.size.toString(), 9, '0') +
                    numeric(balanceTotal, 17, 0) + numeric(accruedTotal, 17, 2),
                RECORD_LENGTH,
            ),
        )
        w.write("\n")
        w.flush()
        return accounts.size
    }

    /** ホストからの未払利息ファイルを取り込み, 未払利息を上書きする. */
    @Transactional
    @Throws(IOException::class)
    fun importAccrued(input: InputStream): ImportResult {
        val lines: List<String> = IOUtils.readLines(input, HOST_CHARSET)
        var processingDate: String? = null
        var updated = 0
        var skipped = 0
        var declaredCount = -1L
        for (line in lines) {
            if (line.trim { it <= ' ' }.isEmpty()) {
                continue
            }
            val kind = line[0]
            when (kind) {
                'H' -> processingDate = line.substring(1, 9)
                'T' -> declaredCount = line.substring(1, 10).toLong()
                'D' -> {
                    if (line.length < RECORD_LENGTH) {
                        throw BankingException("UB-9001", "レコード長不正: $line")
                    }
                    val branch = line.substring(1, 4)
                    val accountNo = line.substring(4, 11)
                    val accrued = BigDecimal(line.substring(35, 52)).movePointLeft(2)
                    val account: Account? =
                        accountRepository.findByBranchCodeAndAccountNo(branch, accountNo)
                    if (account == null) {
                        skipped++
                        continue
                    }
                    account.accruedInterest = accrued
                    accountRepository.save(account)
                    updated++
                }
                else -> throw BankingException("UB-9002", "不明なレコード区分: $kind")
            }
        }
        if (declaredCount >= 0 && declaredCount != (updated + skipped).toLong()) {
            throw BankingException(
                "UB-9003",
                "トレーラ件数不一致: 宣言=$declaredCount 実績=${updated + skipped}",
            )
        }
        return ImportResult(processingDate, updated, skipped)
    }

    class ImportResult @JvmOverloads constructor(
        val processingDate: String? = null,
        val updated: Int = 0,
        val skipped: Int = 0,
    )

    companion object {
        @JvmField
        val HOST_CHARSET: Charset = Charset.forName("MS932")

        const val RECORD_LENGTH = 52

        private val YYYYMMDD: DateTimeFormatter = DateTimeFormat.forPattern("yyyyMMdd")

        /** パッケージ内部用 */
        @JvmStatic
        fun formatRecord(a: Account): String {
            val sb = StringBuilder(RECORD_LENGTH)
            sb.append('D')
            sb.append(a.branchCode)
            sb.append(a.accountNo)
            val type: AccountType = a.accountType ?: throw NullPointerException("accountType")
            sb.append(type.zenginCode)
            sb.append(statusFlag(a.status))
            sb.append(numeric(a.balance, 15, 0))
            sb.append(numeric(a.interestRate, 7, 4))
            sb.append(numeric(a.accruedInterest, 17, 2))
            return sb.toString()
        }

        /** パッケージ内部用 */
        @JvmStatic
        fun numeric(value: BigDecimal, totalDigits: Int, scale: Int): String {
            val digits =
                value.setScale(scale, BigDecimal.ROUND_DOWN).movePointRight(scale).toPlainString()
            if (digits.length > totalDigits) {
                throw BankingException("UB-9004", "桁あふれ: $value")
            }
            return StringUtils.leftPad(digits, totalDigits, '0')
        }

        /** パッケージ内部用 */
        @JvmStatic
        fun statusFlag(status: AccountStatus): Char =
            when (status) {
                AccountStatus.ACTIVE -> 'A'
                AccountStatus.FROZEN -> 'F'
                AccountStatus.DORMANT -> 'D'
                AccountStatus.CLOSED -> 'C'
            }
    }
}
