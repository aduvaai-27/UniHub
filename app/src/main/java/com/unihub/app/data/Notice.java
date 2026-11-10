package com.unihub.app.data;

import com.unihub.app.util.TrustedTime;
import com.google.firebase.firestore.DocumentId;

/** Firestore "notices" document. */
public class Notice {
    @DocumentId public String id;
    public String title, body, author;
    public long timestamp;

    public Notice() { }

    public Notice(String title, String body, String author) {
        this.title = title; this.body = body; this.author = author;
        this.timestamp = TrustedTime.now();
    }
}
