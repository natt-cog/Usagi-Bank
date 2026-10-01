package jp.usagi.bank.repository

import jp.usagi.bank.domain.Branch
import org.springframework.data.jpa.repository.JpaRepository

interface BranchRepository : JpaRepository<Branch, String>
