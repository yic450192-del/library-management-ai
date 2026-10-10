package com.example.library.domain.circulation;

import com.example.library.domain.catalog.LibraryItem;
import com.example.library.domain.reader.Reader;
import com.example.library.domain.shared.LoanStatus;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * 借阅记录。一次借出行为的凭证，承载借出 / 应还 / 归还三个时间点。
 * BR-006：应还日期由 BorrowPolicy 计算后传入，本类不做规则判断。
 * 状态必须能区分借出、已还、超期（数据库设计审查点）。
 */
public class Loan {

    private final Long id;
    private final Reader reader;
    private final LibraryItem item;
    private final Long librarianId;
    private final LocalDate borrowDate;
    private LocalDate dueDate;
    private LocalDate returnDate;
    private LoanStatus status;
    private int renewCount;

    public Loan(Long id, Reader reader, LibraryItem item, Long librarianId,
                LocalDate borrowDate, LocalDate dueDate) {
        this.id = id;
        this.reader = Objects.requireNonNull(reader, "借阅人不能为空");
        this.item = Objects.requireNonNull(item, "馆藏副本不能为空");
        this.librarianId = librarianId;
        this.borrowDate = Objects.requireNonNull(borrowDate, "借出日期不能为空");
        this.dueDate = Objects.requireNonNull(dueDate, "应还日期不能为空");
        this.status = LoanStatus.BORROWED;
        this.renewCount = 0;
    }

    /**
     * 是否超期。信息专家原则：掌握 dueDate 的类自己判断。
     */
    public boolean isOverdue(LocalDate today) {
        return status.isActive() && today.isAfter(dueDate);
    }

    public long overdueDays(LocalDate today) {
        if (!isOverdue(today)) {
            return 0L;
        }
        return ChronoUnit.DAYS.between(dueDate, today);
    }

    public void markReturned(LocalDate returnDate) {
        this.returnDate = Objects.requireNonNull(returnDate, "归还日期不能为空");
        this.status = LoanStatus.RETURNED;
    }

    public void markOverdue() {
        if (status == LoanStatus.BORROWED) {
            this.status = LoanStatus.OVERDUE;
        }
    }

    public void markLost() {
        this.status = LoanStatus.LOST;
    }

    /** 续借：延长应还日期，次数由 BorrowPolicy 校验后调用。 */
    public void renew(int days) {
        this.dueDate = dueDate.plusDays(days);
        this.renewCount++;
    }

    public Long id() {
        return id;
    }

    public Reader reader() {
        return reader;
    }

    public LibraryItem item() {
        return item;
    }

    public Long librarianId() {
        return librarianId;
    }

    public LocalDate borrowDate() {
        return borrowDate;
    }

    public LocalDate dueDate() {
        return dueDate;
    }

    public LocalDate returnDate() {
        return returnDate;
    }

    public LoanStatus status() {
        return status;
    }

    public int renewCount() {
        return renewCount;
    }
}
