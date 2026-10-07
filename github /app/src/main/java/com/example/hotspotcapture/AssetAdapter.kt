package com.example.hotspotcapture

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AssetAdapter(
    private val items: List<CapturedAsset>,
    private val onShare: (CapturedAsset) -> Unit
) : RecyclerView.Adapter<AssetAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvType: TextView = v.findViewById(R.id.tvType)
        val tvName: TextView = v.findViewById(R.id.tvName)
        val tvUrl: TextView = v.findViewById(R.id.tvUrl)
        val btnShare: Button = v.findViewById(R.id.btnShare)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_asset, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val a = items[position]
        holder.tvType.text = a.type.uppercase()
        holder.tvName.text = a.file.name
        holder.tvUrl.text = a.url
        holder.btnShare.setOnClickListener { onShare(a) }
    }

    override fun getItemCount() = items.size
}
