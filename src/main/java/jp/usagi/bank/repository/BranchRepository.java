package jp.usagi.bank.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import jp.usagi.bank.domain.Branch;

public interface BranchRepository extends JpaRepository<Branch, String> {
}
