package com.unihub.app.data;

import com.google.firebase.firestore.DocumentId;

/** Firestore "tasks" document: an assignment or an exam shared with every student. */
public class TaskItem {
    public static final String ASSIGNMENT = "ASSIGNMENT", EXAM = "EXAM";

    @DocumentId public String id;
    public String type, title, course, details, author;
    public long due, createdAt;

    public TaskItem() { }
}
