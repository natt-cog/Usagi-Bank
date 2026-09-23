package jp.usagi.bank.service;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.joda.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jp.usagi.bank.domain.Account;
import jp.usagi.bank.domain.Branch;
import jp.usagi.bank.domain.Transaction;
import jp.usagi.bank.repository.BranchRepository;
import jp.usagi.bank.repository.TransactionRepository;
import jp.usagi.bank.xml.Statement;
import jp.usagi.bank.xml.StatementEntry;

/** 取引明細書 (XML) 生成. */
@Service
public class StatementService {

    private final TransactionRepository transactionRepository;
    private final BranchRepository branchRepository;
    private JAXBContext jaxbContext;

    public StatementService(TransactionRepository transactionRepository, BranchRepository branchRepository) {
        this.transactionRepository = transactionRepository;
        this.branchRepository = branchRepository;
    }

    @PostConstruct
    public void init() throws JAXBException {
        jaxbContext = JAXBContext.newInstance(Statement.class);
    }

    @Transactional(readOnly = true)
    public Statement buildStatement(Account account, LocalDate from, LocalDate to) {
        List<Transaction> txns = transactionRepository.findByAccountIdAndValueDateBetweenOrderByPostedAtAscIdAsc(
                account.getId(), from.toDate(), to.toDate());
        Branch branch = branchRepository.findOne(account.getBranchCode());

        Statement st = new Statement();
        st.setGeneratedAt(new Date());
        st.setBranchCode(account.getBranchCode());
        st.setBranchName(branch == null ? "" : branch.getName());
        st.setAccountNo(account.getAccountNo());
        st.setAccountType(account.getAccountType().getLabel());
        st.setCustomerName(account.getCustomer().getNameKanji());
        st.setPeriodFrom(from.toDate());
        st.setPeriodTo(to.toDate());

        BigDecimal opening = account.getBalance();
        if (!txns.isEmpty()) {
            Transaction first = txns.get(0);
            opening = first.getType().isCredit()
                    ? first.getBalanceAfter().subtract(first.getAmount())
                    : first.getBalanceAfter().add(first.getAmount());
        }
        st.setOpeningBalance(opening);
        st.setClosingBalance(txns.isEmpty() ? opening : txns.get(txns.size() - 1).getBalanceAfter());

        for (Transaction t : txns) {
            StatementEntry e = new StatementEntry();
            e.setValueDate(t.getValueDate());
            e.setType(t.getType().getLabel());
            e.setDescription(t.getDescription());
            if (t.getType().isCredit()) {
                e.setDeposit(t.getAmount());
            } else {
                e.setWithdrawal(t.getAmount());
            }
            e.setBalance(t.getBalanceAfter());
            e.setReferenceNo(t.getReferenceNo());
            st.getEntries().add(e);
        }
        return st;
    }

    public String toXml(Statement statement) {
        try {
            Marshaller m = jaxbContext.createMarshaller();
            m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            m.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
            StringWriter w = new StringWriter();
            m.marshal(statement, w);
            return w.toString();
        } catch (JAXBException e) {
            throw new BankingException("UB-9101", "明細書XML生成に失敗しました: " + e.getMessage());
        }
    }
}
