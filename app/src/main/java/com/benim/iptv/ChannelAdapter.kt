package com.benim.iptv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

class ChannelAdapter(
    private val onClick: (Channel) -> Unit,
    private val onFav: (Channel) -> Unit,
    private val isFav: (String) -> Boolean,
    private val onLongPress: (Channel) -> Unit = {}
) : RecyclerView.Adapter<ChannelAdapter.VH>() {

    private val items = ArrayList<Channel>()

    fun submit(list: List<Channel>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.tv_name)
        val logo: ImageView = v.findViewById(R.id.iv_logo)
        val fav: ImageView = v.findViewById(R.id.iv_fav)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_channel, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val ch = items[position]
        holder.name.text = ch.name

        if (ch.icon.isNotEmpty()) {
            holder.logo.load(ch.icon) {
                placeholder(R.drawable.ic_tv)
                error(R.drawable.ic_tv)
                crossfade(true)
            }
        } else {
            holder.logo.setImageResource(R.drawable.ic_tv)
        }

        holder.fav.setImageResource(
            if (isFav(ch.streamId)) R.drawable.ic_star_on else R.drawable.ic_star_off
        )

        holder.itemView.setOnClickListener { onClick(ch) }
        // Kumandada OK'a basılı tutunca favori menüsü
        holder.itemView.setOnLongClickListener { onLongPress(ch); true }
        holder.fav.setOnClickListener {
            onFav(ch)
            holder.fav.setImageResource(
                if (isFav(ch.streamId)) R.drawable.ic_star_on else R.drawable.ic_star_off
            )
        }
    }

    override fun getItemCount(): Int = items.size
}
