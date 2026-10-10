package com.example.library.domain.shared;

/** 馆藏副本状态（BR-007：只有 IN_LIBRARY 可借出）。 */
public enum ItemStatus {
    IN_LIBRARY,
    ON_LOAN,
    OVERDUE,
    LOST;

    public boolean isAvailable() {
        return this == IN_LIBRARY;
    }
}
