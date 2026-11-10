package com.unihub.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.unihub.app.data.AttendanceRecord;
import com.unihub.app.data.ClassSession;
import com.unihub.app.data.CustomHoliday;
import com.unihub.app.databinding.ItemSessionBinding;
import com.unihub.app.util.Holidays;
import com.unihub.app.util.TimeUtil;
import com.unihub.app.util.TrustedTime;
import com.unihub.app.util.Ui;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Today's classes. Attendance can be given only while the class is in progress. */
public class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.VH> {
    public interface OnMark { void onMark(ClassSession s); }

    private final List<ClassSession> items = new ArrayList<>();
    private Map<String, AttendanceRecord> mine = new HashMap<>();
    private Set<String> cancelled = new HashSet<>();
    private List<CustomHoliday> holidays = new ArrayList<>();
    private final OnMark cb;
    private boolean blocked = false;                    // true while the phone is offline

    public SessionAdapter(OnMark cb) { this.cb = cb; }

    /** Offline: the Present button cannot be used. */
    public void setBlocked(boolean blocked) {
        if (this.blocked == blocked) return;
        this.blocked = blocked;
        notifyDataSetChanged();
    }

    public void submit(List<ClassSession> list, Map<String, AttendanceRecord> mine, Set<String> cancelled,
                       List<CustomHoliday> holidays) {
        items.clear(); items.addAll(list);
        this.mine = mine; this.cancelled = cancelled; this.holidays = holidays;
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        return new VH(ItemSessionBinding.inflate(LayoutInflater.from(p.getContext()), p, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        ClassSession s = items.get(pos);
        long now = TrustedTime.now();
        boolean hasGroup = s.r.group != null && !s.r.group.isEmpty();
        h.b.tvGroup.setText(hasGroup ? "Lab Group " + s.r.group : "Whole Section");
        Ui.tag(h.b.tvGroup, Ui.SLATE);
        h.b.tvTime.setText(TimeUtil.formatMin(s.r.startMin) + " - " + TimeUtil.formatMin(s.r.endMin));
        h.b.tvCourse.setText(Ui.nz(s.r.course));
        h.b.tvSubject.setText(Ui.nz(s.r.title) + (s.r.teacher == null || s.r.teacher.isEmpty() ? "" : "   |   " + s.r.teacher));

        AttendanceRecord rec = mine.get(s.key());
        h.b.btnPresent.setVisibility(View.GONE);
        h.b.btnPresent.setEnabled(true);                    // stays tappable: a tap while offline shows "Failed"
        h.b.btnPresent.setAlpha(blocked ? 0.5f : 1f);
        CustomHoliday hol = Holidays.blocking(holidays, s.r, s.date);
        if (hol != null) {                                   // CR holiday: the class is cancelled automatically
            h.b.tvStatus.setText("Holiday"); Ui.solid(h.b.tvStatus, Ui.PLUM, Ui.WHITE);
            h.b.tvState.setText(Holidays.purpose(hol) + ". No class today.");
        } else if (cancelled.contains(s.key())) {
            h.b.tvStatus.setText("Not Held"); Ui.solid(h.b.tvStatus, Ui.GRAY, Ui.WHITE);
            h.b.tvState.setText("This class was not held.");
        } else if (rec != null) {
            h.b.tvStatus.setText("Present"); Ui.solid(h.b.tvStatus, Ui.GREEN, Ui.WHITE);
            h.b.tvState.setText(rec.markedAt == null ? "Recorded."
                    : "Recorded at " + new SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(rec.markedAt));
        } else if (!TrustedTime.isVerified()) {            // after a reboot with no internet the phone clock is not trusted
            h.b.tvStatus.setText("Time Not Verified"); Ui.solid(h.b.tvStatus, Ui.GRAY, Ui.WHITE);
            h.b.tvState.setText("Connect to the internet once so UniHub can check the real time.");
        } else if (now < s.start) {
            h.b.tvStatus.setText("Not Started"); Ui.solid(h.b.tvStatus, Ui.AMBER, Ui.WHITE);
            h.b.tvState.setText("Attendance opens at " + TimeUtil.formatMin(s.r.startMin) + ".");
        } else if (now < s.end) {
            h.b.tvStatus.setText("In Progress"); Ui.solid(h.b.tvStatus, Ui.BLUE, Ui.WHITE);
            h.b.btnPresent.setVisibility(View.VISIBLE);
            h.b.tvState.setText(blocked ? "You are offline. Attendance needs an internet connection."
                    : "Attendance closes at " + TimeUtil.formatMin(s.r.endMin) + ".");
        } else {
            h.b.tvStatus.setText("Absent"); Ui.solid(h.b.tvStatus, Ui.RED, Ui.WHITE);
            h.b.tvState.setText("Attendance was not recorded during the class.");
        }
        h.b.btnPresent.setOnClickListener(v -> cb.onMark(s));
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final ItemSessionBinding b;
        VH(ItemSessionBinding b) { super(b.getRoot()); this.b = b; }
    }
}
