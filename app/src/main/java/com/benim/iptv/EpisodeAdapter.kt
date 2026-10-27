package com.benim.iptv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class EpisodeAdapter(private val onClick: (Int) -> Unit) :
    RecyclerView.Adapter<EpisodeAdapter.VH>() {

    private val items = ArrayList<Episode>()

    fun submit(list: List<Episode>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tv: TextView = v.findViewById(R.id.tv_ep)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_episode, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = items[position]
        val label = buildString {
            if (e.season.isNotEmpty()) append("S${e.season} ")
            if (e.epNum.isNotEmpty()) append("B${e.epNum}")
            if (e.title.isNotEmpty()) {
                if (isNotEmpty()) append(" · ")
                append(e.title)
            }
        }
        holder.tv.text = label
        holder.itemView.setOnClickListener { onClick(holder.bindingAdapterPosition) }
    }

    override fun getItemCount(): Int = items.size
}
