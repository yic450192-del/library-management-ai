package com.example.library.domain.shared;

/** 借阅证状态（BR-002：非 ACTIVE 不得借书）。 */
public enum CardStatus {
    ACTIVE,
    SUSPENDED,
    CANCELLED;

    public boolean isValid() {
        return this == ACTIVE;
    }
}
