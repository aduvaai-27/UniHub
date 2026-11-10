package com.unihub.app.data;

/** One message of the class group chat (stored in Firebase Realtime Database under chat/messages). */
public class ChatMessage {
    public String key;          // push id, not stored inside the message
    public String uid, name, roll, role, text;
    public long timestamp;

    public ChatMessage() { }    // required by Firebase
}
