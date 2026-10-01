package jp.usagi.bank.service

import java.io.StringWriter
import java.math.BigDecimal
import java.util.Date

import javax.annotation.PostConstruct
import javax.xml.bind.JAXBContext
import javax.xml.bind.JAXBException
import javax.xml.bind.Marshaller

import org.joda.time.LocalDate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

import jp.usagi.bank.domain.Account
import jp.usagi.bank.domain.Branch
import jp.usagi.bank.domain.Transaction
import jp.usagi.bank.repository.BranchRepository
import jp.usagi.bank.repository.TransactionRepository
import jp.usagi.bank.xml.Statement
import jp.usagi.bank.xml.StatementEntry

/** 取引明細書 (XML) 生成. */
@Service
class StatementService(
    private val transactionRepository: TransactionRepository,
    private val branchRepository: BranchRepository
) {

    private lateinit var jaxbContext: JAXBContext

    @PostConstruct
    @Throws(JAXBException::class)
    fun init() {
        jaxbContext = JAXBContext.newInstance(Statement::class.java)
    }

    @Transactional(readOnly = true)
    fun buildStatement(account: Account, from: LocalDate, to: LocalDate): Statement {
        val txns: List<Transaction> = transactionRepository.findByAccountIdAndValueDateBetweenOrderByPostedAtAscIdAsc(
            account.id, from.toDate(), to.toDate())
        val branch: Branch? = branchRepository.findOne(account.branchCode)

        val st = Statement()
        st.generatedAt = Date()
        st.branchCode = account.branchCode
        st.branchName = branch?.name ?: ""
        st.accountNo = account.accountNo
        st.accountType = account.accountType.label
        st.customerName = account.customer.nameKanji
        st.periodFrom = from.toDate()
        st.periodTo = to.toDate()

        var opening: BigDecimal = account.balance
        if (txns.isNotEmpty()) {
            val first = txns[0]
            opening = if (first.type.isCredit)
                first.balanceAfter.subtract(first.amount)
            else
                first.balanceAfter.add(first.amount)
        }
        st.openingBalance = opening
        st.closingBalance = if (txns.isEmpty()) opening else txns[txns.size - 1].balanceAfter

        for (t in txns) {
            val e = StatementEntry()
            e.valueDate = t.valueDate
            e.type = t.type.label
            e.description = t.description
            if (t.type.isCredit) {
                e.deposit = t.amount
            } else {
                e.withdrawal = t.amount
            }
            e.balance = t.balanceAfter
            e.referenceNo = t.referenceNo
            st.entries.add(e)
        }
        return st
    }

    fun toXml(statement: Statement): String {
        try {
            val m = jaxbContext.createMarshaller()
            m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, java.lang.Boolean.TRUE)
            m.setProperty(Marshaller.JAXB_ENCODING, "UTF-8")
            val w = StringWriter()
            m.marshal(statement, w)
            return w.toString()
        } catch (e: JAXBException) {
            throw BankingException("UB-9101", "明細書XML生成に失敗しました: " + e.message)
        }
    }
}
