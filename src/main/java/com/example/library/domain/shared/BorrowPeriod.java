package com.example.library.domain.shared;

import java.time.LocalDate;

/**
 * 借阅期限值对象（单位：天）。
 */
public record BorrowPeriod(int days) {

    public BorrowPeriod {
        if (days <= 0) {
            throw new IllegalArgumentException("借阅期限必须为正数，实际为 " + days);
        }
    }

    /** BR-006：应还日期 = 借出日期 + 期限 */
    public LocalDate dueDateFrom(LocalDate borrowDate) {
        return borrowDate.plusDays(days);
    }
}
