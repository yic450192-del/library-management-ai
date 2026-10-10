package com.example.library.domain.exception;

/** 业务错误码。表现层据此映射 HTTP 语义（见 07-architecture.md 第 5 节）。 */
public enum ErrorCode {

    CARD_NOT_FOUND(404, "借阅证不存在"),
    CARD_NOT_ACTIVE(409, "借阅证已挂起或注销"),
    READER_NOT_ALLOWED(409, "读者账号状态不允许借书"),
    QUOTA_EXCEEDED(409, "已达借阅上限"),
    OVERDUE_LOAN_EXISTS(409, "存在超期未还图书"),
    ITEM_NOT_OWNED(404, "非本馆藏书"),
    ITEM_NOT_AVAILABLE(409, "该副本当前不可借"),
    LOAN_NOT_FOUND(409, "该副本当前未借出"),
    RESERVATION_DUPLICATE(409, "您已预约该题名"),
    TITLE_HAS_COPIES(409, "题名下仍有馆藏副本，不可删除"),
    CARD_HAS_ACTIVE_LOANS(409, "借阅证下存在在借记录，不可注销"),
    ROLE_FORBIDDEN(403, "当前角色无权执行该操作");

    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
