package com.unihub.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.unihub.app.data.CustomHoliday;
import com.unihub.app.data.RoutineEntry;
import com.unihub.app.databinding.ItemRoutineBinding;
import com.unihub.app.util.Holidays;
import com.unihub.app.util.TimeUtil;
import com.unihub.app.util.Ui;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RoutineAdapter extends RecyclerView.Adapter<RoutineAdapter.VH> {
    public interface OnDelete { void onDelete(RoutineEntry r); }

    private final List<RoutineEntry> items = new ArrayList<>();
    private final boolean staff;
    private final OnDelete cb;
    private boolean isToday;
    private Set<String> cancelled = new HashSet<>();     // "date_routineId" of cancelled classes
    private List<CustomHoliday> holidays = new ArrayList<>();   // CR holidays: weekly classes on those dates are not held
    private String dayDate = "";                         // date of the weekday being shown (for weekly classes)
    private OnTap tap;                                   // CR: tap on an extra / cancelled class

    public interface OnTap { void onTap(RoutineEntry r, boolean cancelled); }

    public RoutineAdapter(boolean staff, OnDelete cb) { this.staff = staff; this.cb = cb; }

    public void setOnTap(OnTap tap) { this.tap = tap; }

    public void submit(List<RoutineEntry> list, boolean today, Set<String> cancelled, List<CustomHoliday> holidays, String dayDate) {
        items.clear(); items.addAll(list); isToday = today;
        this.cancelled = cancelled; this.holidays = holidays; this.dayDate = dayDate; notifyDataSetChanged();
    }

    private boolean isOff(RoutineEntry r) { return cancelled.contains((r.oneTime() ? r.date : dayDate) + "_" + r.id); }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        return new VH(ItemRoutineBinding.inflate(LayoutInflater.from(p.getContext()), p, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        RoutineEntry r = items.get(pos);
        h.b.strip.setBackgroundColor(Ui.colorFor(r.course));
        h.b.tvStart.setText(TimeUtil.formatMin(r.startMin));
        h.b.tvEnd.setText("to " + TimeUtil.formatMin(r.endMin));
        h.b.tvCourse.setText(Ui.nz(r.course));
        h.b.tvTitle.setText(Ui.nz(r.title));
        StringBuilder meta = new StringBuilder();
        if (r.teacher != null && !r.teacher.isEmpty()) meta.append(meta.length() > 0 ? "   |   " : "").append("Teacher: ").append(r.teacher);
        if (r.room != null && !r.room.isEmpty()) meta.append(meta.length() > 0 ? "   |   " : "").append("Room: ").append(r.room);
        h.b.tvMeta.setText(meta);
        int now = TimeUtil.nowMin();
        boolean extra = r.oneTime();
        String todayKey = TimeUtil.dateKey(TimeUtil.dayStart(com.unihub.app.util.TrustedTime.now()));
        boolean live = isToday && (!extra || r.date.equals(todayKey)) && now >= r.startMin && now < r.endMin;
        boolean hasGroup = r.group != null && !r.group.isEmpty();
        CustomHoliday hol = Holidays.blocking(holidays, r, dayDate);
        boolean cancelledByCr = isOff(r);                     // cancelled one by one (this one can be undone by tapping)
        boolean off = cancelledByCr || hol != null;
        h.b.tagRow.setVisibility(live || hasGroup || extra || off ? View.VISIBLE : View.GONE);
        h.b.tvOff.setVisibility(off ? View.VISIBLE : View.GONE);
        if (hol != null) { h.b.tvOff.setText("Holiday | " + Holidays.purpose(hol) + " | " + TimeUtil.prettyDate(dayDate)); Ui.solid(h.b.tvOff, Ui.PLUM, Ui.WHITE); }
        else if (off) { h.b.tvOff.setText("Cancelled | " + TimeUtil.prettyDate(r.oneTime() ? r.date : dayDate)); Ui.solid(h.b.tvOff, Ui.RED, Ui.WHITE); }
        h.b.tvExtra.setVisibility(extra ? View.VISIBLE : View.GONE);
        if (extra) { h.b.tvExtra.setText("Extra class | " + TimeUtil.prettyDate(r.date)); Ui.tag(h.b.tvExtra, Ui.AMBER); }
        h.b.tvLive.setVisibility(live && !off ? View.VISIBLE : View.GONE);
        if (live && !off) Ui.solid(h.b.tvLive, Ui.GREEN, Ui.WHITE);
        h.b.tvGroup.setVisibility(hasGroup ? View.VISIBLE : View.GONE);
        if (hasGroup) { h.b.tvGroup.setText("Lab Group " + r.group); Ui.tag(h.b.tvGroup, Ui.SLATE); }
        h.b.btnDelete.setVisibility(staff ? View.VISIBLE : View.GONE);
        h.b.btnDelete.setOnClickListener(v -> cb.onDelete(r));
        boolean tappable = staff && tap != null && (extra || cancelledByCr);      // CR: tap an extra or cancelled class to undo it
        h.itemView.setOnClickListener(tappable ? v -> tap.onTap(r, cancelledByCr) : null);
        h.itemView.setClickable(tappable);
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final ItemRoutineBinding b;
        VH(ItemRoutineBinding b) { super(b.getRoot()); this.b = b; }
    }
}
