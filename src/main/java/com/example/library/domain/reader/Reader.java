package com.example.library.domain.reader;

import com.example.library.domain.circulation.Loan;
import com.example.library.domain.shared.AccountStatus;
import com.example.library.domain.shared.LoanStatus;
import com.example.library.domain.shared.ReaderType;

import java.util.List;
import java.util.Objects;

/**
 * 读者（抽象实体）。借阅行为的责任主体。
 * 学生读者与教师读者泛化自本类；借阅额度与期限不在此硬编码，交给 BorrowPolicy。
 */
public abstract class Reader {

    private final Long id;
    private String name;
    private String department;
    private final ReaderType readerType;
    private AccountStatus status;

    protected Reader(Long id, String name, String department, ReaderType readerType) {
        this.id = id;
        this.name = Objects.requireNonNull(name, "读者姓名不能为空");
        this.department = Objects.requireNonNull(department, "系别或单位不能为空");
        this.readerType = Objects.requireNonNull(readerType, "读者类型不能为空");
        this.status = AccountStatus.ACTIVE;
    }

    /**
     * BR-002 / 账号状态：非 ACTIVE 不得借书。
     */
    public boolean isAllowedToBorrow() {
        return status == AccountStatus.ACTIVE;
    }

    /**
     * BR-004：在借数量 = 状态为 BORROWED / OVERDUE 的记录数。
     * 借阅记录由调用方传入，实体不持有集合，避免实体依赖 Repository。
     */
    public int activeLoanCount(List<Loan> loans) {
        return (int) loans.stream().filter(l -> l.status().isActive()).count();
    }

    /**
     * BR-005：是否存在超期未还图书。
     */
    public boolean hasOverdueLoan(List<Loan> loans, java.time.LocalDate today) {
        return loans.stream().anyMatch(l -> l.isOverdue(today));
    }

    public List<Loan> overdueLoans(List<Loan> loans, java.time.LocalDate today) {
        return loans.stream().filter(l -> l.isOverdue(today)).toList();
    }

    public Long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String department() {
        return department;
    }

    public ReaderType readerType() {
        return readerType;
    }

    public AccountStatus status() {
        return status;
    }

    public void suspend() {
        this.status = AccountStatus.SUSPENDED;
    }

    public void activate() {
        this.status = AccountStatus.ACTIVE;
    }
}
