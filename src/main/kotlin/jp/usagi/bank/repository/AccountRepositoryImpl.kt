package jp.usagi.bank.repository

import java.math.BigDecimal

import javax.persistence.EntityManager
import javax.persistence.PersistenceContext

import org.hibernate.Criteria
import org.hibernate.Session
import org.hibernate.criterion.Order
import org.hibernate.criterion.Restrictions

import jp.usagi.bank.domain.Account
import jp.usagi.bank.domain.AccountStatus
import jp.usagi.bank.domain.AccountType

/**
 * Hibernate Criteria API による動的検索.
 * (JPA Criteria は冗長なため旧来の Hibernate Criteria を使用)
 */
class AccountRepositoryImpl : AccountRepositoryCustom {

    @PersistenceContext
    lateinit var entityManager: EntityManager

    @Suppress("UNCHECKED_CAST")
    override fun search(branchCode: String?, type: AccountType?, status: AccountStatus?,
            minBalance: BigDecimal?, maxBalance: BigDecimal?, maxResults: Int): List<Account> {
        val session: Session = entityManager.unwrap(Session::class.java)
        val criteria: Criteria = session.createCriteria(Account::class.java)
        if (branchCode != null && !branchCode.isEmpty()) {
            criteria.add(Restrictions.eq("branchCode", branchCode))
        }
        if (type != null) {
            criteria.add(Restrictions.eq("accountType", type))
        }
        if (status != null) {
            criteria.add(Restrictions.eq("status", status))
        }
        if (minBalance != null) {
            criteria.add(Restrictions.ge("balance", minBalance))
        }
        if (maxBalance != null) {
            criteria.add(Restrictions.le("balance", maxBalance))
        }
        criteria.addOrder(Order.asc("branchCode")).addOrder(Order.asc("accountNo"))
        criteria.setMaxResults(maxResults)
        return criteria.list() as List<Account>
    }
}
