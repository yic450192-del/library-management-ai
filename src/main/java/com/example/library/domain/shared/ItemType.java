package com.example.library.domain.shared;

/** 借出物类型。决定适用哪条 FineRule（指导书需求 5、需求 7）。 */
public enum ItemType {
    CN_BOOK,          // 中文图书
    FOREIGN_BOOK,     // 外文图书
    CN_MAGAZINE,      // 中文杂志
    FOREIGN_MAGAZINE, // 外文杂志
    THESIS            // 论文
}
