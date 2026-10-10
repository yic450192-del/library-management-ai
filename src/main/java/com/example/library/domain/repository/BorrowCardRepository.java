package com.example.library.domain.repository;

import com.example.library.domain.card.BorrowCard;

import java.util.Optional;

/** 领域层定义的持久化接口，实现在基础设施层（依赖倒置 DIP）。 */
public interface BorrowCardRepository {

    Optional<BorrowCard> findByCardNo(String cardNo);

    Optional<BorrowCard> findActiveByReaderId(Long readerId);

    BorrowCard save(BorrowCard card);
}
