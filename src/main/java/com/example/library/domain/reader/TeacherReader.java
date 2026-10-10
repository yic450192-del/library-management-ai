package com.example.library.domain.reader;

import com.example.library.domain.shared.ReaderType;

/** 教师读者。 */
public class TeacherReader extends Reader {

    private final String staffNo;
    private final String title;

    public TeacherReader(Long id, String name, String department,
                         String staffNo, String title) {
        super(id, name, department, ReaderType.TEACHER);
        this.staffNo = staffNo;
        this.title = title;
    }

    public String staffNo() {
        return staffNo;
    }

    public String title() {
        return title;
    }
}
