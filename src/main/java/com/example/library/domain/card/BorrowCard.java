package com.example.library.domain.card;

import com.example.library.domain.shared.CardStatus;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 借阅证。指导书原文："借阅证可以提供学生的姓名、系别和借阅证号"。
 * BR-001：证号格式 CARD + 年份 + 6 位序号，全局唯一。
 * BR-002：状态非 ACTIVE 时不得借书。
 */
public class BorrowCard {

    private final Long id;
    private final String cardNo;
    private final Long readerId;
    private final String ownerName;
    private final String department;
    private final LocalDate issuedAt;
    private CardStatus status;

    public BorrowCard(Long id, String cardNo, Long readerId,
                      String ownerName, String department, LocalDate issuedAt) {
        this.id = id;
        this.cardNo = Objects.requireNonNull(cardNo, "借阅证号不能为空");
        this.readerId = Objects.requireNonNull(readerId, "读者 id 不能为空");
        this.ownerName = ownerName;
        this.department = department;
        this.issuedAt = issuedAt;
        this.status = CardStatus.ACTIVE;
    }

    public boolean isValid() {
        return status.isValid();
    }

    public void suspend() {
        this.status = CardStatus.SUSPENDED;
    }

    /** 注销前由应用服务校验：该证下无在借记录（BR-015）。 */
    public void cancel() {
        this.status = CardStatus.CANCELLED;
    }

    public Long id() {
        return id;
    }

    public String cardNo() {
        return cardNo;
    }

    public Long readerId() {
        return readerId;
    }

    public String ownerName() {
        return ownerName;
    }

    public String department() {
        return department;
    }

    public LocalDate issuedAt() {
        return issuedAt;
    }

    public CardStatus status() {
        return status;
    }
}
