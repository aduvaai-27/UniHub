package com.unihub.app.data;

import com.google.firebase.firestore.DocumentId;
import java.util.Date;

/** Firestore "attendance" document: one student, present in one class session. Cannot be changed later. */
public class AttendanceRecord {
    @DocumentId public String id;
    public String uid, roll, name, group, date, routineId, sessionKey, course;
    public Date markedAt;

    public AttendanceRecord() { }
}
