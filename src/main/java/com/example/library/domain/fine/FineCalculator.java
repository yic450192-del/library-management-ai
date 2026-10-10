package com.example.library.domain.fine;

import com.example.library.domain.circulation.Loan;

import java.time.LocalDate;

/**
 * 罚款计算领域服务（UC-903）。
 * 计算行为横跨 Loan 与 FineRule，不属于任何单个实体，因此提升为领域服务。
 */
public class FineCalculator {

    /**
     * 按借出物类型取规则并计算罚款。
     * @return 罚款记录；未超期或处于宽限期内时返回 null（不产生罚款）
     */
    public FineRecord calculateAndCreate(Long fineId, Loan loan, FineRule rule, LocalDate today) {
        long overdueDays = loan.overdueDays(today);
        if (overdueDays <= 0) {
            return null;
        }
        var amount = rule.calculate(overdueDays);
        if (!amount.isPositive()) {
            return null;
        }
        return new FineRecord(fineId, loan.id(), loan.reader().id(),
                overdueDays, amount, today);
    }
}
