package com.unihub.app.util;

import android.content.Context;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.unihub.app.data.Notice;
import com.unihub.app.data.RoutineEntry;

/** CR only: undo an extra class (delete it) or a cancellation (the class is held again). Students get a notice either way. */
public class ClassUndo {
    private ClassUndo() { }

    public static void confirmUndoExtra(Context c, RoutineEntry r, Runnable online) {
        new AlertDialog.Builder(c)
                .setTitle("Undo extra class?")
                .setMessage("The extra " + Ui.nz(r.course) + " class of " + r.scopeLabel() + " on " + TimeUtil.prettyDate(r.date)
                        + " (" + TimeUtil.formatMin(r.startMin) + " - " + TimeUtil.formatMin(r.endMin)
                        + ") will be removed, and the students will be notified.")
                .setPositiveButton("Undo Extra Class", (d, w) -> { if (online == null) doUndoExtra(c, r); else online.run(); })
                .setNegativeButton("Back", null)
                .show();
    }

    public static void doUndoExtra(Context c, RoutineEntry r) {
        if (!OfflineWatcher.requireOnline(c)) return;
        FirebaseFirestore fs = FirebaseFirestore.getInstance();
        Notice n = new Notice("Extra Class Cancelled: " + Ui.nz(r.course),
                "The extra " + Ui.nz(r.course) + " class of " + r.scopeLabel() + " on " + TimeUtil.prettyDate(r.date) + " ("
                        + TimeUtil.formatMin(r.startMin) + " - " + TimeUtil.formatMin(r.endMin) + ") will not be held.",
                Session.name(c) + " (CR)");
        WriteBatch batch = fs.batch();
        batch.delete(fs.collection("routine").document(r.id));
        batch.set(fs.collection("notices").document(), n);
        batch.commit()
                .addOnSuccessListener(x -> Toast.makeText(c, "Extra class removed. Students have been notified.", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(c, Ui.firebaseError(e), Toast.LENGTH_LONG).show());
    }

    public static void confirmUndoCancel(Context c, RoutineEntry r, String dateKey) {
        new AlertDialog.Builder(c)
                .setTitle("Undo cancellation?")
                .setMessage("The " + Ui.nz(r.course) + " class of " + r.scopeLabel() + " on " + TimeUtil.prettyDate(dateKey)
                        + " (" + TimeUtil.formatMin(r.startMin) + " - " + TimeUtil.formatMin(r.endMin)
                        + ") will be held again, and the students will be notified.")
                .setPositiveButton("Undo Cancellation", (d, w) -> doUndoCancel(c, r, dateKey))
                .setNegativeButton("Back", null)
                .show();
    }

    public static void doUndoCancel(Context c, RoutineEntry r, String dateKey) {
        if (!OfflineWatcher.requireOnline(c)) return;
        FirebaseFirestore fs = FirebaseFirestore.getInstance();
        Notice n = new Notice("Class Will Be Held: " + Ui.nz(r.course),
                "The " + Ui.nz(r.course) + " class of " + r.scopeLabel() + " on " + TimeUtil.prettyDate(dateKey) + " ("
                        + TimeUtil.formatMin(r.startMin) + " - " + TimeUtil.formatMin(r.endMin) + ") is no longer cancelled. It will be held.",
                Session.name(c) + " (CR)");
        WriteBatch batch = fs.batch();
        batch.delete(fs.collection("cancellations").document(dateKey + "_" + r.id));
        batch.set(fs.collection("notices").document(), n);
        batch.commit()
                .addOnSuccessListener(x -> Toast.makeText(c, "Cancellation removed. Students have been notified.", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(c, Ui.firebaseError(e), Toast.LENGTH_LONG).show());
    }
}
