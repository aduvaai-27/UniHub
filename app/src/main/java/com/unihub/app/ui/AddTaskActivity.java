package com.unihub.app.ui;

import com.unihub.app.util.TrustedTime;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.unihub.app.data.Notice;
import com.unihub.app.data.TaskItem;
import com.unihub.app.databinding.ActivityAddTaskBinding;
import com.unihub.app.util.Session;
import com.unihub.app.util.TimeUtil;
import com.unihub.app.util.Ui;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

/** CR only: post an assignment or exam. The date is chosen from lists and the time from scroll wheels.
 *  Saving writes the task and a notice; the notice sends the notification to every student. */
public class AddTaskActivity extends BaseActivity {
    public static final String EXTRA_TYPE = "type";
    private ActivityAddTaskBinding b;
    private String type;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityAddTaskBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        if (!Session.isStaff(this)) { finish(); return; }

        type = getIntent().getStringExtra(EXTRA_TYPE);
        b.tvHeader.setText(TaskItem.EXAM.equals(type) ? "Post Exam" : "Post Assignment");
        b.tvDateLabel.setText(TaskItem.EXAM.equals(type) ? "Exam date and time" : "Deadline");

        Calendar t = TimeUtil.calendarNow();
        t.add(Calendar.DAY_OF_YEAR, 1);                       // default deadline: tomorrow
        List<String> days = new ArrayList<>();
        for (int d = 1; d <= 31; d++) days.add(String.valueOf(d));
        List<String> years = new ArrayList<>();
        int y = TimeUtil.calendarNow().get(Calendar.YEAR);
        for (int i = 0; i < 3; i++) years.add(String.valueOf(y + i));

        fill(b.acDay, days, String.valueOf(t.get(Calendar.DAY_OF_MONTH)));
        fill(b.acMonth, Arrays.asList(TimeUtil.MONTHS_SHORT), TimeUtil.MONTHS_SHORT[t.get(Calendar.MONTH)]);
        fill(b.acYear, years, String.valueOf(t.get(Calendar.YEAR)));
        b.twTime.setMinutes(23 * 60 + 59);                    // 11:59 PM
        b.btnSave.setOnClickListener(v -> save());
    }

    private void fill(AutoCompleteTextView view, List<String> items, String selected) {
        view.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, items));
        view.setText(selected, false);
    }

    @Override protected android.view.View offlineBar() { return b == null ? null : b.tvOffline; }

    /** Offline: saving is blocked. */
    @Override protected void onConnectivityChanged(boolean on) { setNeedsInternet(b.btnSave, on); }

    private void save() {
        if (!requireOnline()) return;
        String title = b.etTitle.getText().toString().trim();
        if (title.isEmpty()) { b.etTitle.setError("Required"); return; }

        long day = TimeUtil.dateMillis(Integer.parseInt(b.acDay.getText().toString()),
                Arrays.asList(TimeUtil.MONTHS_SHORT).indexOf(b.acMonth.getText().toString()),
                Integer.parseInt(b.acYear.getText().toString()));
        if (day < 0) { Toast.makeText(this, "The selected date does not exist.", Toast.LENGTH_SHORT).show(); return; }

        long due = day + b.twTime.getMinutes() * 60000L;
        if (due <= TrustedTime.now()) {                       // a deadline or exam time in the past is not accepted
            Toast.makeText(this, "The date and time are in the past. Choose a future date and time.", Toast.LENGTH_LONG).show();
            return;
        }

        TaskItem task = new TaskItem();
        task.type = type; task.title = title;
        task.course = b.etCourse.getText().toString().trim();
        task.details = b.etDetails.getText().toString().trim();
        task.due = due;
        task.createdAt = TrustedTime.now();
        task.author = Session.name(this);

        // The CR's post is written to "tasks" AND to "notices" in one batch. The notice is what alerts the students:
        // NoticeService turns every new notice into a notification on every other account (no separate task alert).
        boolean exam = TaskItem.EXAM.equals(type);
        String kind = exam ? "Exam" : "Assignment";
        StringBuilder body = new StringBuilder();
        body.append(task.course.isEmpty() ? "" : task.course + ": ").append(title)
                .append(exam ? ". Exam on " : ". Due on ").append(TimeUtil.dateTime(task.due)).append(".");
        if (!task.details.isEmpty()) body.append(" ").append(task.details);
        Notice notice = new Notice("New " + kind + ": " + title, body.toString(), Session.name(this) + " (CR)");

        FirebaseFirestore fs = FirebaseFirestore.getInstance();
        WriteBatch batch = fs.batch();
        batch.set(fs.collection("tasks").document(), task);
        batch.set(fs.collection("notices").document(), notice);
        b.btnSave.setEnabled(false);
        batch.commit()
                .addOnSuccessListener(x -> { Toast.makeText(this, "Posted successfully. Students have been notified.", Toast.LENGTH_SHORT).show(); finish(); })
                .addOnFailureListener(e -> { setNeedsInternet(b.btnSave, online);
                    Toast.makeText(this, Ui.firebaseError(e), Toast.LENGTH_LONG).show(); });
    }
}
