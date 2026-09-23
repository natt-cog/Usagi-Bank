package jp.usagi.bank.repository;

import java.math.BigDecimal;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;

import jp.usagi.bank.domain.Account;
import jp.usagi.bank.domain.AccountStatus;
import jp.usagi.bank.domain.AccountType;

/**
 * Hibernate Criteria API による動的検索.
 * (JPA Criteria は冗長なため旧来の Hibernate Criteria を使用)
 */
public class AccountRepositoryImpl implements AccountRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @SuppressWarnings("unchecked")
    public List<Account> search(String branchCode, AccountType type, AccountStatus status,
            BigDecimal minBalance, BigDecimal maxBalance, int maxResults) {
        Session session = entityManager.unwrap(Session.class);
        Criteria criteria = session.createCriteria(Account.class);
        if (branchCode != null && !branchCode.isEmpty()) {
            criteria.add(Restrictions.eq("branchCode", branchCode));
        }
        if (type != null) {
            criteria.add(Restrictions.eq("accountType", type));
        }
        if (status != null) {
            criteria.add(Restrictions.eq("status", status));
        }
        if (minBalance != null) {
            criteria.add(Restrictions.ge("balance", minBalance));
        }
        if (maxBalance != null) {
            criteria.add(Restrictions.le("balance", maxBalance));
        }
        criteria.addOrder(Order.asc("branchCode")).addOrder(Order.asc("accountNo"));
        criteria.setMaxResults(maxResults);
        return criteria.list();
    }
}
