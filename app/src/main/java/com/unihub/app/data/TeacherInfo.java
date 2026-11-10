package com.unihub.app.data;

/** One teacher inside a course card (stored inside the "teacherCards" document). */
public class TeacherInfo {
    public String name, shortName, dept, email, phone;

    public TeacherInfo() { }

    public TeacherInfo(String shortName, String name, String dept, String email, String phone) {
        this.shortName = shortName; this.name = name; this.dept = dept; this.email = email; this.phone = phone;
    }
}
