package com.pkm.said.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pkm.said.databinding.ItemFeatureBinding
import com.pkm.said.FeatureItem

class FeatureAdapter(
    private val items: List<FeatureItem>,
    private val onClick: (FeatureItem) -> Unit
) : RecyclerView.Adapter<FeatureAdapter.FeatureVH>() {

    inner class FeatureVH(val binding: ItemFeatureBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FeatureItem) {
            binding.ivIcon.setImageResource(item.iconRes)
            binding.tvTitle.text = item.title
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeatureVH {
        val binding = ItemFeatureBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FeatureVH(binding)
    }

    override fun onBindViewHolder(holder: FeatureVH, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size
}