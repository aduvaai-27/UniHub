package com.unihub.app.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.unihub.app.data.Notice;
import com.unihub.app.databinding.ItemNoticeBinding;
import com.unihub.app.util.TimeUtil;
import com.unihub.app.util.Ui;
import java.util.List;

/** Notices shown in a classic ListView: an ArrayAdapter that reuses the row views (convertView) of the notice cards. */
public class NoticeAdapter extends ArrayAdapter<Notice> {
    public interface OnNotice { void on(Notice n); }

    private final boolean staff;
    private final OnNotice onDelete;

    public NoticeAdapter(Context context, boolean staff, OnNotice onDelete) {
        super(context, 0);
        this.staff = staff; this.onDelete = onDelete;
    }

    public void submit(List<Notice> list) { clear(); addAll(list); notifyDataSetChanged(); }

    @NonNull @Override
    public View getView(int pos, @Nullable View convertView, @NonNull ViewGroup parent) {
        ItemNoticeBinding b;
        if (convertView == null) {
            b = ItemNoticeBinding.inflate(LayoutInflater.from(getContext()), parent, false);
            b.getRoot().setTag(b);
        } else {
            b = (ItemNoticeBinding) convertView.getTag();
        }
        Notice n = getItem(pos);
        b.tvTitle.setText(Ui.nz(n.title));
        b.tvBody.setText(Ui.nz(n.body));
        b.tvMeta.setText("Posted by " + Ui.nz(n.author) + "   |   " + TimeUtil.dateTime(n.timestamp));
        b.btnDelete.setVisibility(staff ? View.VISIBLE : View.GONE);
        b.btnDelete.setOnClickListener(v -> onDelete.on(n));
        return b.getRoot();
    }
}
