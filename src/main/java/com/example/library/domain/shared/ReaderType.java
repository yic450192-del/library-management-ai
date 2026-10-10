package com.example.library.domain.shared;

/** 读者类型。决定适用哪条 BorrowPolicy（指导书需求 4）。 */
public enum ReaderType {
    JUNIOR_COLLEGE,   // 专科生
    UNDERGRADUATE,    // 本科生
    POSTGRADUATE,     // 研究生
    DOCTORAL,         // 博士生
    TEACHER           // 教师
}
