package com.unihub.app.data;

import com.google.firebase.firestore.DocumentId;

/** Firestore "cancellations" document: a class session that was not held. Id = session key. */
public class Cancellation {
    @DocumentId public String id;
    public String sessionKey, date, routineId, by;

    public Cancellation() { }
}
