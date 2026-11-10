package com.unihub.app.data;

import com.google.firebase.firestore.DocumentId;

/** Firestore "students" document (id = roll). group is A1, A2, B1 or B2, empty if not set. */
public class StudentInfo {
    @DocumentId public String id;
    public String roll, name, phone, email, group;

    public StudentInfo() { }

    public StudentInfo(String roll, String name, String phone, String email) {
        this.roll = roll; this.name = name; this.phone = phone; this.email = email; this.group = "";
    }
}
