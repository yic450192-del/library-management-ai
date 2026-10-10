package com.example.library.domain.fine;

import com.example.library.domain.shared.FineStatus;
import com.example.library.domain.shared.Money;

import java.time.LocalDate;
import java.util.Objects;

/** 罚款记录。实验一不实现真实扣款，只登记（见 00-project-brief 暂不实现清单）。 */
public class FineRecord {

    private final Long id;
    private final Long loanId;
    private final Long readerId;
    private final long overdueDays;
    private final Money amount;
    private final LocalDate createdAt;
    private FineStatus status;

    public FineRecord(Long id, Long loanId, Long readerId,
                      long overdueDays, Money amount, LocalDate createdAt) {
        this.id = id;
        this.loanId = Objects.requireNonNull(loanId, "来源借阅记录不能为空");
        this.readerId = Objects.requireNonNull(readerId, "欠款人不能为空");
        this.overdueDays = overdueDays;
        this.amount = Objects.requireNonNull(amount, "罚款金额不能为空");
        this.createdAt = createdAt;
        this.status = FineStatus.UNPAID;
    }

    public boolean isOutstanding() {
        return status == FineStatus.UNPAID;
    }

    public void markPaid() {
        this.status = FineStatus.PAID;
    }

    public void waive() {
        this.status = FineStatus.WAIVED;
    }

    public Long id() {
        return id;
    }

    public Long loanId() {
        return loanId;
    }

    public Long readerId() {
        return readerId;
    }

    public long overdueDays() {
        return overdueDays;
    }

    public Money amount() {
        return amount;
    }

    public LocalDate createdAt() {
        return createdAt;
    }

    public FineStatus status() {
        return status;
    }
}
