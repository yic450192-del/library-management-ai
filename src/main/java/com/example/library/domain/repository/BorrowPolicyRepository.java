package com.example.library.domain.repository;

import com.example.library.domain.policy.BorrowPolicy;
import com.example.library.domain.shared.ReaderType;

/** 按读者类型取借阅规则（指导书需求 4）。 */
public interface BorrowPolicyRepository {

    BorrowPolicy findByReaderType(ReaderType readerType);
}
