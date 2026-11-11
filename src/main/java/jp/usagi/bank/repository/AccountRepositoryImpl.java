package jp.usagi.bank.repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import jp.usagi.bank.domain.Account;
import jp.usagi.bank.domain.AccountStatus;
import jp.usagi.bank.domain.AccountType;

/** JPA Criteria API による動的検索. */
public class AccountRepositoryImpl implements AccountRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<Account> search(String branchCode, AccountType type, AccountStatus status,
            BigDecimal minBalance, BigDecimal maxBalance, int maxResults) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Account> query = cb.createQuery(Account.class);
        Root<Account> root = query.from(Account.class);

        List<Predicate> predicates = new ArrayList<>();
        if (branchCode != null && !branchCode.isEmpty()) {
            predicates.add(cb.equal(root.get("branchCode"), branchCode));
        }
        if (type != null) {
            predicates.add(cb.equal(root.get("accountType"), type));
        }
        if (status != null) {
            predicates.add(cb.equal(root.get("status"), status));
        }
        if (minBalance != null) {
            predicates.add(cb.ge(root.get("balance"), minBalance));
        }
        if (maxBalance != null) {
            predicates.add(cb.le(root.get("balance"), maxBalance));
        }
        query.select(root)
                .where(predicates.toArray(new Predicate[0]))
                .orderBy(cb.asc(root.get("branchCode")), cb.asc(root.get("accountNo")));
        return entityManager.createQuery(query).setMaxResults(maxResults).getResultList();
    }
}
