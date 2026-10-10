package com.example.library.domain.catalog;

import com.example.library.domain.shared.ItemStatus;
import com.example.library.domain.shared.ItemType;

import java.util.Objects;

/**
 * 馆藏副本（借出物），抽象基类。
 * 子类：Book / Magazine / Thesis（指导书需求 5：中外文图书、中外文杂志、论文）。
 * BR-007：只有 IN_LIBRARY 的副本可借出。
 * BR-008：归还时必须为本馆藏书。
 */
public abstract class LibraryItem {

    private final Long id;
    private final String barcode;
    private final Long titleId;
    private final ItemType itemType;
    private String location;
    private ItemStatus status;
    private int version;

    protected LibraryItem(Long id, String barcode, Long titleId, ItemType itemType) {
        this.id = id;
        this.barcode = Objects.requireNonNull(barcode, "馆藏条码不能为空");
        this.titleId = Objects.requireNonNull(titleId, "所属题名 id 不能为空");
        this.itemType = Objects.requireNonNull(itemType, "借出物类型不能为空");
        this.status = ItemStatus.IN_LIBRARY;
        this.version = 0;
    }

    public boolean isAvailable() {
        return status.isAvailable();
    }

    public void markOnLoan() {
        if (!isAvailable()) {
            throw new IllegalStateException("副本 " + barcode + " 当前状态为 " + status + "，不可借出");
        }
        this.status = ItemStatus.ON_LOAN;
    }

    public void markReturned() {
        this.status = ItemStatus.IN_LIBRARY;
    }

    public void markOverdue() {
        if (status == ItemStatus.ON_LOAN) {
            this.status = ItemStatus.OVERDUE;
        }
    }

    public void markLost() {
        this.status = ItemStatus.LOST;
    }

    public Long id() {
        return id;
    }

    public String barcode() {
        return barcode;
    }

    public Long titleId() {
        return titleId;
    }

    public ItemType itemType() {
        return itemType;
    }

    public String location() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public ItemStatus status() {
        return status;
    }

    /** 乐观锁版本号，防止并发重复借出（见架构文档第 6 节）。 */
    public int version() {
        return version;
    }
}
