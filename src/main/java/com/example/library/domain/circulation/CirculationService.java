package com.example.library.domain.circulation;

import com.example.library.domain.card.BorrowCard;
import com.example.library.domain.catalog.LibraryItem;
import com.example.library.domain.exception.BusinessException;
import com.example.library.domain.exception.ErrorCode;
import com.example.library.domain.fine.FineCalculator;
import com.example.library.domain.fine.FineRecord;
import com.example.library.domain.fine.FineRule;
import com.example.library.domain.policy.BorrowPolicy;
import com.example.library.domain.reader.Reader;
import com.example.library.domain.repository.BorrowCardRepository;
import com.example.library.domain.repository.BorrowPolicyRepository;
import com.example.library.domain.repository.FineRuleRepository;
import com.example.library.domain.repository.LibraryItemRepository;
import com.example.library.domain.repository.LoanRepository;
import com.example.library.domain.repository.ReaderRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * 借阅领域服务：编排 UC-101 办理借书 与 UC-102 办理还书。
 *
 * 为什么是领域服务而不是实体方法：借书横跨 BorrowCard / Reader / BorrowPolicy /
 * LibraryItem / Loan 五个对象，不属于任何一个实体内部，因此提升为领域服务。
 *
 * 本类只依赖领域接口，不依赖 Spring、不依赖持久化框架（NFR-003）。
 * Clock 注入而非直接 LocalDate.now()，便于实验二的自动化测试构造"超期"场景。
 */
public class CirculationService {

    private final BorrowCardRepository cardRepository;
    private final ReaderRepository readerRepository;
    private final LibraryItemRepository itemRepository;
    private final LoanRepository loanRepository;
    private final BorrowPolicyRepository policyRepository;
    private final FineRuleRepository ruleRepository;
    private final FineCalculator fineCalculator;
    private final Clock clock;

    public CirculationService(BorrowCardRepository cardRepository,
                              ReaderRepository readerRepository,
                              LibraryItemRepository itemRepository,
                              LoanRepository loanRepository,
                              BorrowPolicyRepository policyRepository,
                              FineRuleRepository ruleRepository,
                              FineCalculator fineCalculator,
                              Clock clock) {
        this.cardRepository = cardRepository;
        this.readerRepository = readerRepository;
        this.itemRepository = itemRepository;
        this.loanRepository = loanRepository;
        this.policyRepository = policyRepository;
        this.ruleRepository = ruleRepository;
        this.fineCalculator = fineCalculator;
        this.clock = clock;
    }

    /**
     * UC-101 办理借书。对应 specs/10-sequence-borrow-book.puml。
     * 校验顺序严格按指导书原文：验证借阅证 → 检查借阅数量 → 检查超期未还 → 校验副本 → 创建记录。
     */
    public Loan borrow(String cardNo, String barcode, Long librarianId) {
        LocalDate today = LocalDate.now(clock);

        // 1. 验证借阅证（UC-901）
        BorrowCard card = cardRepository.findByCardNo(cardNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));
        if (!card.isValid()) {
            throw new BusinessException(ErrorCode.CARD_NOT_ACTIVE);
        }

        Reader reader = readerRepository.findById(card.readerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));
        if (!reader.isAllowedToBorrow()) {
            throw new BusinessException(ErrorCode.READER_NOT_ALLOWED);
        }

        // 2. 检查借阅规则（UC-902）
        BorrowPolicy policy = policyRepository.findByReaderType(reader.readerType());
        List<Loan> activeLoans = loanRepository.findActiveByReaderId(reader.id());

        // BR-004：在借数量是否已达上限
        if (!policy.checkQuota(reader.activeLoanCount(activeLoans))) {
            throw new BusinessException(ErrorCode.QUOTA_EXCEEDED,
                    "已达借阅上限（" + policy.maxBooks() + " 册）");
        }
        // BR-005：是否存在超期未还图书
        if (reader.hasOverdueLoan(activeLoans, today)) {
            throw new BusinessException(ErrorCode.OVERDUE_LOAN_EXISTS);
        }

        // 3. 校验馆藏副本（BR-007 / BR-008）
        LibraryItem item = itemRepository.findByBarcode(barcode)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_OWNED));
        if (!item.isAvailable()) {
            throw new BusinessException(ErrorCode.ITEM_NOT_AVAILABLE);
        }

        // 4. 创建借阅记录（BR-006）
        Loan loan = new Loan(null, reader, item, librarianId,
                today, policy.calculateDueDate(today));
        item.markOnLoan();
        itemRepository.save(item);
        return loanRepository.save(loan);
    }

    /**
     * UC-102 办理还书。对应 specs/11-sequence-return-book.puml。
     * 指导书原文：还书时学生只需把图书交给管理员，管理员输入图书信息 —— 因此不需要借阅证。
     */
    public ReturnResult returnItem(String barcode, Long librarianId) {
        LocalDate today = LocalDate.now(clock);

        // 1. 验证本馆藏书（BR-008）
        LibraryItem item = itemRepository.findByBarcode(barcode)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_OWNED));

        Loan loan = loanRepository.findActiveByItemId(item.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.LOAN_NOT_FOUND));

        // 2. 超期则计算罚款（UC-903 扩展点，BR-009）
        FineRecord fine = null;
        if (loan.isOverdue(today)) {
            loan.markOverdue();
            FineRule rule = ruleRepository.findByItemType(item.itemType());
            fine = fineCalculator.calculateAndCreate(null, loan, rule, today);
        }

        // 3. 结清借阅信息并更新副本
        loan.markReturned(today);
        item.markReturned();
        itemRepository.save(item);
        return new ReturnResult(loanRepository.save(loan), fine);
    }

    /** 还书结果：结清的借阅记录 + 可能产生的罚款记录。 */
    public record ReturnResult(Loan loan, FineRecord fine) {
    }
}
