package com.benim.iptv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

/** Izgarada afiş + ad gösteren genel öğe (film/dizi). */
data class Tile(val title: String, val image: String)

class MediaAdapter(private val onClick: (Int) -> Unit) :
    RecyclerView.Adapter<MediaAdapter.VH>() {

    private val items = ArrayList<Tile>()

    fun submit(list: List<Tile>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.tv_name)
        val logo: ImageView = v.findViewById(R.id.iv_logo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_tile, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val t = items[position]
        holder.name.text = t.title
        if (t.image.isNotEmpty()) {
            holder.logo.load(t.image) {
                placeholder(R.drawable.ic_movie)
                error(R.drawable.ic_movie)
                crossfade(true)
            }
        } else {
            holder.logo.setImageResource(R.drawable.ic_movie)
        }
        holder.itemView.setOnClickListener { onClick(holder.bindingAdapterPosition) }
    }

    override fun getItemCount(): Int = items.size
}
