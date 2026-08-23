package com.watchnavigator.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.watchnavigator.databinding.ItemPlaceSuggestionBinding
import com.watchnavigator.model.PlaceSuggestion

class PlaceSuggestionsAdapter(
    private val onSuggestionClicked: (PlaceSuggestion) -> Unit
) : ListAdapter<PlaceSuggestion, PlaceSuggestionsAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPlaceSuggestionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemPlaceSuggestionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(suggestion: PlaceSuggestion) {
            binding.tvPrimaryText.text = suggestion.primaryText
            binding.tvSecondaryText.text = suggestion.secondaryText
            binding.root.setOnClickListener {
                onSuggestionClicked(suggestion)
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<PlaceSuggestion>() {
        override fun areItemsTheSame(oldItem: PlaceSuggestion, newItem: PlaceSuggestion): Boolean {
            return oldItem.placeId == newItem.placeId
        }

        override fun areContentsTheSame(oldItem: PlaceSuggestion, newItem: PlaceSuggestion): Boolean {
            return oldItem == newItem
        }
    }
}
