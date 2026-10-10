package com.example.library.domain.reader;

import com.example.library.domain.shared.ReaderType;
import com.example.library.domain.shared.StudentCategory;

/** 学生读者：专科生 / 本科生 / 研究生 / 博士生。 */
public class StudentReader extends Reader {

    private final String studentNo;
    private final StudentCategory category;

    public StudentReader(Long id, String name, String department,
                         ReaderType readerType, String studentNo, StudentCategory category) {
        super(id, name, department, readerType);
        this.studentNo = studentNo;
        this.category = category;
    }

    public String studentNo() {
        return studentNo;
    }

    public StudentCategory category() {
        return category;
    }
}
