package jp.usagi.bank.service;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jp.usagi.bank.domain.Customer;
import jp.usagi.bank.domain.KycStatus;
import jp.usagi.bank.repository.CustomerRepository;

@Service
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public Customer getByCifNo(String cifNo) {
        Customer c = customerRepository.findByCifNo(cifNo);
        if (c == null) {
            throw new CustomerNotFoundException(cifNo);
        }
        return c;
    }

    public Customer getById(Long id) {
        Customer c = customerRepository.findById(id).orElse(null);
        if (c == null) {
            throw new CustomerNotFoundException(String.valueOf(id));
        }
        return c;
    }

    public Page<Customer> search(String keyword, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Direction.ASC, "nameKana"));
        if (keyword == null || keyword.trim().isEmpty()) {
            return customerRepository.findAll(pageable);
        }
        return customerRepository.findByNameKanjiContainingOrNameKanaContaining(keyword, keyword, pageable);
    }

    public List<Customer> topCustomers(int limit) {
        return customerRepository.findTopByTotalBalance(limit);
    }

    @Transactional
    public Customer register(Customer customer) {
        if (StringUtils.isBlank(customer.getCifNo())) {
            customer.setCifNo(nextCifNo());
        }
        if (customerRepository.findByCifNo(customer.getCifNo()) != null) {
            throw new BankingException("UB-1005", "CIF番号が重複しています: " + customer.getCifNo());
        }
        return customerRepository.save(customer);
    }

    /** CIF 採番: MAX+1 (同時登録時の重複は UK 制約で検知する運用). */
    private String nextCifNo() {
        long next = Long.parseLong(customerRepository.findMaxCifNo()) + 1;
        return String.format("%010d", next);
    }

    @Transactional
    public void updateKyc(Long id, KycStatus status) {
        Customer c = getById(id);
        c.setKycStatus(status);
        customerRepository.save(c);
    }
}
