package jp.usagi.bank.repository;

import java.math.BigDecimal;
import java.util.List;

import jp.usagi.bank.domain.Account;
import jp.usagi.bank.domain.AccountStatus;
import jp.usagi.bank.domain.AccountType;

/** 動的検索 (口座検索画面用). */
public interface AccountRepositoryCustom {

    List<Account> search(String branchCode, AccountType type, AccountStatus status,
            BigDecimal minBalance, BigDecimal maxBalance, int maxResults);
}
