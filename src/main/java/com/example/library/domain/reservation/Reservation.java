package com.example.library.domain.reservation;

import com.example.library.domain.shared.ReservationStatus;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 预约记录。BR-010：面向题名而非副本，按创建时间排队。
 * BR-011：同一读者对同一题名只能有一条有效预约。
 * BR-012：预约保留 3 天，逾期失效并顺延。
 */
public class Reservation {

    public static final int HOLD_DAYS = 3;

    private final Long id;
    private final Long readerId;
    private final Long titleId;
    private final LocalDateTime createdAt;
    private int queuePosition;
    private ReservationStatus status;
    private LocalDateTime expireAt;

    public Reservation(Long id, Long readerId, Long titleId, int queuePosition) {
        this.id = id;
        this.readerId = Objects.requireNonNull(readerId, "预约人不能为空");
        this.titleId = Objects.requireNonNull(titleId, "题名不能为空");
        this.queuePosition = queuePosition;
        this.createdAt = LocalDateTime.now();
        this.status = ReservationStatus.WAITING;
    }

    public boolean isExpired(LocalDateTime now) {
        return expireAt != null && now.isAfter(expireAt);
    }

    /** 副本归还，通知队首读者取书，开始 3 天保留期。 */
    public void ready(LocalDateTime now) {
        this.status = ReservationStatus.READY;
        this.expireAt = now.plusDays(HOLD_DAYS);
    }

    public void expire() {
        this.status = ReservationStatus.EXPIRED;
    }

    public void fulfill() {
        this.status = ReservationStatus.FULFILLED;
    }

    public void cancel() {
        this.status = ReservationStatus.CANCELLED;
    }

    /** 队列顺延：前方有人退出时位次前移。 */
    public void moveForward() {
        if (queuePosition > 1) {
            this.queuePosition--;
        }
    }

    public Long id() {
        return id;
    }

    public Long readerId() {
        return readerId;
    }

    public Long titleId() {
        return titleId;
    }

    public int queuePosition() {
        return queuePosition;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    public ReservationStatus status() {
        return status;
    }

    public LocalDateTime expireAt() {
        return expireAt;
    }
}
