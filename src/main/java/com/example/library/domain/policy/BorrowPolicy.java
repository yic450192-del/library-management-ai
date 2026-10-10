package com.example.library.domain.policy;

import com.example.library.domain.shared.BorrowPeriod;
import com.example.library.domain.shared.ReaderType;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 借阅规则（Strategy 模式的一条策略）。
 * 指导书需求 4：不同类型的读者可以借阅的图书数量和期限都是不同的。
 * 业务规则集中在这里，不散落到 Controller（NFR-002）。
 */
public class BorrowPolicy {

    private final Long id;
    private final ReaderType readerType;
    private final int maxBooks;
    private final BorrowPeriod loanPeriod;
    private final int maxRenewTimes;
    private final int renewDays;

    public BorrowPolicy(Long id, ReaderType readerType, int maxBooks,
                        int loanPeriodDays, int maxRenewTimes, int renewDays) {
        this.id = id;
        this.readerType = Objects.requireNonNull(readerType, "读者类型不能为空");
        this.maxBooks = maxBooks;
        this.loanPeriod = new BorrowPeriod(loanPeriodDays);
        this.maxRenewTimes = maxRenewTimes;
        this.renewDays = renewDays;
    }

    /** BR-004：在借数量是否已达上限。 */
    public boolean checkQuota(int activeLoanCount) {
        return activeLoanCount < maxBooks;
    }

    /** BR-006：应还日期 = 借出日期 + 期限。 */
    public LocalDate calculateDueDate(LocalDate borrowDate) {
        return loanPeriod.dueDateFrom(borrowDate);
    }

    public boolean canRenew(int renewCount) {
        return renewCount < maxRenewTimes;
    }

    public Long id() {
        return id;
    }

    public ReaderType readerType() {
        return readerType;
    }

    public int maxBooks() {
        return maxBooks;
    }

    public BorrowPeriod loanPeriod() {
        return loanPeriod;
    }

    public int maxRenewTimes() {
        return maxRenewTimes;
    }

    public int renewDays() {
        return renewDays;
    }
}
