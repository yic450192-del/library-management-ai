package com.example.library.domain.shared;

/** 借阅记录状态。必须能区分借出、已还、超期（数据库设计审查点）。 */
public enum LoanStatus {
    BORROWED,
    RETURNED,
    OVERDUE,
    LOST;

    /** 是否仍占用读者的借阅额度（BR-004 计数口径） */
    public boolean isActive() {
        return this == BORROWED || this == OVERDUE;
    }
}
