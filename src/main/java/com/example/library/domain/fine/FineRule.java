package com.example.library.domain.fine;

import com.example.library.domain.shared.ItemType;
import com.example.library.domain.shared.Money;

import java.util.Objects;

/**
 * 罚款规则（Strategy 模式的一条策略）。
 * 指导书需求 7：各种类型的借出物罚款规则不同（超期时间的规定和罚金都不同）。
 */
public class FineRule {

    private final Long id;
    private final ItemType itemType;
    private final int graceDays;
    private final Money dailyRate;
    private final Money maxAmount;

    public FineRule(Long id, ItemType itemType, int graceDays,
                    Money dailyRate, Money maxAmount) {
        this.id = id;
        this.itemType = Objects.requireNonNull(itemType, "借出物类型不能为空");
        this.graceDays = Math.max(0, graceDays);
        this.dailyRate = Objects.requireNonNull(dailyRate, "每日罚金不能为空");
        this.maxAmount = Objects.requireNonNull(maxAmount, "罚金上限不能为空");
    }

    /**
     * BR-009：罚款 = 超期天数 × 每日罚金，且不超过单笔上限。
     * @param overdueDays 超期天数（未扣除宽限期）
     */
    public Money calculate(long overdueDays) {
        long chargeableDays = Math.max(0, overdueDays - graceDays);
        if (chargeableDays == 0) {
            return Money.zero();
        }
        return dailyRate.multiply(chargeableDays).cap(maxAmount);
    }

    public Long id() {
        return id;
    }

    public ItemType itemType() {
        return itemType;
    }

    public int graceDays() {
        return graceDays;
    }

    public Money dailyRate() {
        return dailyRate;
    }

    public Money maxAmount() {
        return maxAmount;
    }
}
