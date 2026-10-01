package jp.usagi.bank.service

import org.apache.commons.lang3.StringUtils
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import jp.usagi.bank.domain.Customer
import jp.usagi.bank.domain.KycStatus
import jp.usagi.bank.repository.CustomerRepository

@Service
@Transactional(readOnly = true)
class CustomerService(
    private val customerRepository: CustomerRepository,
) {

    fun getByCifNo(cifNo: String?): Customer =
        customerRepository.findByCifNo(cifNo) ?: throw CustomerNotFoundException(cifNo)

    fun getById(id: Long?): Customer {
        val customer: Customer? = customerRepository.findOne(id)
        return customer ?: throw CustomerNotFoundException(id.toString())
    }

    fun search(keyword: String?, page: Int, size: Int): Page<Customer> {
        val pageable = PageRequest(page, size, Sort(Sort.Direction.ASC, "nameKana"))
        if (keyword == null || keyword.trim { it <= ' ' }.isEmpty()) {
            return customerRepository.findAll(pageable)
        }
        return customerRepository.findByNameKanjiContainingOrNameKanaContaining(keyword, keyword, pageable)
    }

    fun topCustomers(limit: Int): List<Customer> = customerRepository.findTopByTotalBalance(limit)

    @Transactional
    fun register(customer: Customer): Customer {
        if (StringUtils.isBlank(customer.cifNo)) {
            customer.cifNo = nextCifNo()
        }
        if (customerRepository.findByCifNo(customer.cifNo) != null) {
            throw BankingException("UB-1005", "CIF番号が重複しています: ${customer.cifNo}")
        }
        return customerRepository.save(customer)
    }

    /** CIF 採番: MAX+1 (同時登録時の重複は UK 制約で検知する運用). */
    private fun nextCifNo(): String {
        val next = customerRepository.findMaxCifNo().toLong() + 1
        return String.format("%010d", next)
    }

    @Transactional
    fun updateKyc(id: Long?, status: KycStatus) {
        val customer = getById(id)
        customer.kycStatus = status
        customerRepository.save(customer)
    }
}
