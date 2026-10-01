package jp.usagi.bank.service

import jp.usagi.bank.domain.Account
import jp.usagi.bank.domain.Branch
import jp.usagi.bank.domain.Transaction
import jp.usagi.bank.domain.TransactionType
import jp.usagi.bank.repository.BranchRepository
import jp.usagi.bank.repository.TransactionRepository
import jp.usagi.bank.xml.Statement
import jp.usagi.bank.xml.StatementEntry
import org.joda.time.LocalDate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.StringWriter
import java.math.BigDecimal
import java.util.Date
import javax.annotation.PostConstruct
import javax.xml.bind.JAXBContext
import javax.xml.bind.JAXBException
import javax.xml.bind.Marshaller

/** 取引明細書 (XML) 生成. */
@Service
class StatementService(
    private val transactionRepository: TransactionRepository,
    private val branchRepository: BranchRepository,
) {
    private var jaxbContext: JAXBContext? = null

    @PostConstruct
    @Throws(JAXBException::class)
    fun init() {
        jaxbContext = JAXBContext.newInstance(Statement::class.java)
    }

    @Transactional(readOnly = true)
    fun buildStatement(
        account: Account,
        from: LocalDate,
        to: LocalDate,
    ): Statement {
        val txns: List<Transaction> =
            transactionRepository.findByAccountIdAndValueDateBetweenOrderByPostedAtAscIdAsc(
                account.id,
                from.toDate(),
                to.toDate(),
            )
        val branch: Branch? = branchRepository.findOne(account.branchCode)

        val statement = Statement()
        statement.generatedAt = Date()
        statement.branchCode = account.branchCode
        statement.branchName = if (branch == null) "" else branch.name
        statement.accountNo = account.accountNo
        statement.accountType = account.accountType?.label
        statement.customerName = account.customer?.nameKanji
        statement.periodFrom = from.toDate()
        statement.periodTo = to.toDate()

        var opening: BigDecimal = account.balance
        if (txns.isNotEmpty()) {
            val first: Transaction = txns[0]
            val type: TransactionType = checkNotNull(first.type)
            val balanceAfter: BigDecimal = checkNotNull(first.balanceAfter)
            val amount: BigDecimal = checkNotNull(first.amount)
            opening =
                if (type.isCredit) {
                    balanceAfter.subtract(amount)
                } else {
                    balanceAfter.add(amount)
                }
        }
        statement.openingBalance = opening
        statement.closingBalance = if (txns.isEmpty()) opening else txns[txns.size - 1].balanceAfter

        for (transaction in txns) {
            val type: TransactionType = checkNotNull(transaction.type)
            val amount: BigDecimal = checkNotNull(transaction.amount)
            val balanceAfter: BigDecimal = checkNotNull(transaction.balanceAfter)
            val entry = StatementEntry()
            entry.valueDate = transaction.valueDate
            entry.type = type.label
            entry.description = transaction.description
            if (type.isCredit) {
                entry.deposit = amount
            } else {
                entry.withdrawal = amount
            }
            entry.balance = balanceAfter
            entry.referenceNo = transaction.referenceNo
            statement.entries.add(entry)
        }
        return statement
    }

    fun toXml(statement: Statement): String {
        val context = checkNotNull(jaxbContext) { "JAXBContext is not initialized" }
        try {
            val marshaller = context.createMarshaller()
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
            marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8")
            val writer = StringWriter()
            marshaller.marshal(statement, writer)
            return writer.toString()
        } catch (e: JAXBException) {
            throw BankingException("UB-9101", "明細書XML生成に失敗しました: ${e.message}")
        }
    }
}
