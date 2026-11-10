package com.unihub.app.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.unihub.app.data.AttendanceLevel;
import com.unihub.app.data.CourseStat;
import com.unihub.app.databinding.ItemStatBinding;
import com.unihub.app.util.Ui;
import java.util.ArrayList;
import java.util.List;

/** Course-wise attendance summary: one card per course with its own ring and a level (Excellent / Good / Okay / Critical). */
public class StatAdapter extends RecyclerView.Adapter<StatAdapter.VH> {
    private final List<CourseStat> items = new ArrayList<>();

    public void submit(List<CourseStat> list) { items.clear(); items.addAll(list); notifyDataSetChanged(); }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        return new VH(ItemStatBinding.inflate(LayoutInflater.from(p.getContext()), p, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        CourseStat s = items.get(pos);
        int pct = s.percent();
        int color = AttendanceLevel.color(pct);
        h.b.tvName.setText(Ui.nz(s.name));
        h.b.tvStats.setText(s.present + " present, " + s.absent() + " absent, " + s.total + " classes held");
        h.b.tvStatus.setText(AttendanceLevel.label(pct));
        Ui.tag(h.b.tvStatus, color);
        h.b.ring.setPercent(pct);                       // this course's own ring
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final ItemStatBinding b;
        VH(ItemStatBinding b) { super(b.getRoot()); this.b = b; }
    }
}
