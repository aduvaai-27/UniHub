package com.unihub.app.adapter;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.unihub.app.databinding.ItemTileBinding;
import java.util.List;

/** The service tiles on the home screen (shown in a 2-column grid). */
public class TileAdapter extends RecyclerView.Adapter<TileAdapter.VH> {
    public static class Tile {
        final String name, sub;
        final int accent;
        final Class<?> target;
        final Runnable action;                       // used instead of a screen (for example the dark mode choice)
        public Tile(String name, String sub, int accent, Class<?> target) {
            this.name = name; this.sub = sub; this.accent = accent; this.target = target; this.action = null;
        }
        public Tile(String name, String sub, int accent, Runnable action) {
            this.name = name; this.sub = sub; this.accent = accent; this.target = null; this.action = action;
        }
    }

    private final List<Tile> tiles;

    public TileAdapter(List<Tile> tiles) { this.tiles = tiles; }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        return new VH(ItemTileBinding.inflate(LayoutInflater.from(p.getContext()), p, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Tile t = tiles.get(pos);
        h.b.vAccent.setBackgroundColor(t.accent);
        h.b.tvName.setText(t.name);
        h.b.tvSub.setText(t.sub);
        h.b.getRoot().setOnClickListener(v -> {
            if (t.action != null) t.action.run();
            else v.getContext().startActivity(new Intent(v.getContext(), t.target));   // explicit intent
        });
    }

    @Override public int getItemCount() { return tiles.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final ItemTileBinding b;
        VH(ItemTileBinding b) { super(b.getRoot()); this.b = b; }
    }
}
