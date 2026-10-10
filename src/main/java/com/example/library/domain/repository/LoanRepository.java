package com.example.library.domain.repository;

import com.example.library.domain.circulation.Loan;

import java.util.List;
import java.util.Optional;

public interface LoanRepository {

    /** BR-004 / BR-005：查读者的在借记录（BORROWED 与 OVERDUE 都算占用额度） */
    List<Loan> findActiveByReaderId(Long readerId);

    /** 还书时定位在借记录 */
    Optional<Loan> findActiveByItemId(Long itemId);

    Loan save(Loan loan);
}
