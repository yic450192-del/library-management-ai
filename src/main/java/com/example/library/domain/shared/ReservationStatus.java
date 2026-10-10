package com.example.library.domain.shared;

/** 预约状态。 */
public enum ReservationStatus {
    WAITING,     // 排队中
    READY,       // 已通知取书（BR-012：保留 3 天）
    FULFILLED,   // 已借出
    EXPIRED,     // 超时失效，队列顺延
    CANCELLED;   // 读者主动取消

    /** 是否仍占用排队名额（BR-011 判定"已有有效预约"的口径） */
    public boolean isActive() {
        return this == WAITING || this == READY;
    }
}
