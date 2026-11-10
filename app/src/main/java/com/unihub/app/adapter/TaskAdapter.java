package com.unihub.app.adapter;

import com.unihub.app.util.TrustedTime;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.unihub.app.data.TaskItem;
import com.unihub.app.databinding.ItemTaskBinding;
import com.unihub.app.util.TimeUtil;
import com.unihub.app.util.Ui;
import java.util.ArrayList;
import java.util.List;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.VH> {
    public interface OnDelete { void onDelete(TaskItem t); }

    private final List<TaskItem> items = new ArrayList<>();
    private final boolean staff;
    private final OnDelete cb;

    public TaskAdapter(boolean staff, OnDelete cb) { this.staff = staff; this.cb = cb; }

    public void submit(List<TaskItem> list) { items.clear(); items.addAll(list); notifyDataSetChanged(); }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        return new VH(ItemTaskBinding.inflate(LayoutInflater.from(p.getContext()), p, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        TaskItem t = items.get(pos);
        h.b.strip.setBackgroundColor(TaskItem.EXAM.equals(t.type) ? Ui.RED : Ui.BLUE);
        h.b.tvCourse.setText(t.course == null || t.course.isEmpty() ? "General" : t.course);
        Ui.tag(h.b.tvCourse, Ui.colorFor(t.course));
        h.b.tvTitle.setText(Ui.nz(t.title));
        h.b.tvDetails.setText(Ui.nz(t.details));
        h.b.tvDetails.setVisibility(t.details == null || t.details.isEmpty() ? View.GONE : View.VISIBLE);
        h.b.tvDue.setText((TaskItem.EXAM.equals(t.type) ? "Exam: " : "Deadline: ") + TimeUtil.dateTime(t.due));
        h.b.tvCountdown.setText(TimeUtil.countdownText(t.due));
        Ui.solid(h.b.tvCountdown, TimeUtil.countdownColor(t.due), Ui.WHITE);
        h.itemView.setAlpha(t.due < TrustedTime.now() ? 0.6f : 1f);
        h.b.btnDelete.setVisibility(staff ? View.VISIBLE : View.GONE);
        h.b.btnDelete.setOnClickListener(v -> cb.onDelete(t));
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final ItemTaskBinding b;
        VH(ItemTaskBinding b) { super(b.getRoot()); this.b = b; }
    }
}
