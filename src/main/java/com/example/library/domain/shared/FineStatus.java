package com.example.library.domain.shared;

/** 罚款缴纳状态。实验一不实现真实扣款，只登记。 */
public enum FineStatus {
    UNPAID,
    PAID,
    WAIVED
}
