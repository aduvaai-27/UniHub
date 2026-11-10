package com.unihub.app.adapter;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.unihub.app.R;
import com.unihub.app.data.ChatMessage;
import com.unihub.app.databinding.ItemChatBinding;
import com.unihub.app.util.Session;
import com.unihub.app.util.Ui;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Chat bubbles: my messages on the right (navy), everyone else on the left (white). */
public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.VH> {
    private final List<ChatMessage> items = new ArrayList<>();
    private final String myUid;

    public ChatAdapter(String myUid) { this.myUid = myUid; }

    public int add(ChatMessage m) { items.add(m); notifyItemInserted(items.size() - 1); return items.size() - 1; }

    public void update(ChatMessage m) {
        for (int i = 0; i < items.size(); i++)
            if (items.get(i).key.equals(m.key)) { items.set(i, m); notifyItemChanged(i); return; }
    }

    public void remove(String key) {
        for (int i = 0; i < items.size(); i++)
            if (items.get(i).key.equals(key)) { items.remove(i); notifyItemRemoved(i); return; }
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        return new VH(ItemChatBinding.inflate(LayoutInflater.from(p.getContext()), p, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        ChatMessage m = items.get(pos);
        boolean mine = m.uid != null && m.uid.equals(myUid);
        h.b.getRoot().setGravity(mine ? Gravity.END : Gravity.START);
        h.b.bubble.setBackgroundResource(mine ? R.drawable.bg_bubble_me : R.drawable.bg_card);
        int maxW = Math.round(h.itemView.getResources().getDisplayMetrics().widthPixels * 0.78f);
        h.b.bubble.setMinimumWidth(0);
        h.b.tvText.setMaxWidth(maxW);
        h.b.tvSender.setMaxWidth(maxW);

        h.b.tvSender.setVisibility(mine ? View.GONE : View.VISIBLE);
        boolean cr = Session.CR.equals(m.role);
        h.b.tvSender.setText(Ui.nz(m.name) + (cr ? "  (CR)" : "") + "  |  " + Ui.nz(m.roll));
        h.b.tvSender.setTextColor(cr ? Ui.RED : Ui.BLUE);

        h.b.tvText.setText(Ui.nz(m.text));
        h.b.tvText.setTextColor(mine ? Ui.WHITE : h.itemView.getContext().getColor(com.unihub.app.R.color.ink));
        h.b.tvTime.setText(m.timestamp == 0 ? "sending..."
                : new SimpleDateFormat("dd MMM, hh:mm a", Locale.ENGLISH).format(new Date(m.timestamp)));
        h.b.tvTime.setTextColor(mine ? 0xFFCFE0F2 : h.itemView.getContext().getColor(com.unihub.app.R.color.muted));
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final ItemChatBinding b;
        VH(ItemChatBinding b) { super(b.getRoot()); this.b = b; }
    }
}
