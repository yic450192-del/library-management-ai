package com.example.library.domain.repository;

import com.example.library.domain.reservation.Reservation;

import java.util.List;

public interface ReservationRepository {

    /** BR-010：同题名排队人数，用于计算 queuePosition */
    int countActiveByTitleId(Long titleId);

    /** BR-011：同一读者对同一题名是否已有有效预约 */
    boolean existsActiveByReaderAndTitle(Long readerId, Long titleId);

    List<Reservation> findQueueByTitleId(Long titleId);

    Reservation save(Reservation reservation);
}
