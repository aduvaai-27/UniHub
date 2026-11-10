package com.unihub.app.data;

import com.google.firebase.firestore.DocumentId;
import java.util.ArrayList;
import java.util.List;

/** Firestore "teacherCards" document: one course of this semester with the teachers taking it. */
public class CourseInfo {
    @DocumentId public String id;
    public String code, title;
    public List<TeacherInfo> teachers = new ArrayList<>();

    public CourseInfo() { }
}
