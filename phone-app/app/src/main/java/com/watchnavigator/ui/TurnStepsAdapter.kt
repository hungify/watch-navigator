package com.watchnavigator.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.watchnavigator.R
import com.watchnavigator.databinding.ItemNavStepBinding
import com.watchnavigator.model.ManeuverType
import com.watchnavigator.model.NavStep
import com.watchnavigator.util.DistanceFormatter

class TurnStepsAdapter : ListAdapter<NavStep, TurnStepsAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNavStepBinding.inflate(
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
        private val binding: ItemNavStepBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(step: NavStep) {
            binding.tvStreetName.text = step.streetName.ifBlank { step.instruction }
            binding.tvInstruction.text = step.instruction
            binding.tvDistance.text = DistanceFormatter.formatDistance(step.distanceMeters)
            binding.ivManeuver.setImageResource(getManeuverIcon(step.maneuver))
        }

        @DrawableRes
        private fun getManeuverIcon(maneuver: ManeuverType): Int {
            return when (maneuver) {
                ManeuverType.TURN_LEFT -> R.drawable.ic_maneuver_turn_left
                ManeuverType.TURN_RIGHT -> R.drawable.ic_maneuver_turn_right
                ManeuverType.TURN_SLIGHT_LEFT, ManeuverType.RAMP_LEFT, ManeuverType.FORK_LEFT -> R.drawable.ic_maneuver_slight_left
                ManeuverType.TURN_SLIGHT_RIGHT, ManeuverType.RAMP_RIGHT, ManeuverType.FORK_RIGHT -> R.drawable.ic_maneuver_slight_right
                ManeuverType.TURN_SHARP_LEFT -> R.drawable.ic_maneuver_sharp_left
                ManeuverType.TURN_SHARP_RIGHT -> R.drawable.ic_maneuver_sharp_right
                ManeuverType.UTURN_LEFT, ManeuverType.UTURN_RIGHT -> R.drawable.ic_maneuver_uturn
                ManeuverType.ROUNDABOUT_LEFT, ManeuverType.ROUNDABOUT_RIGHT -> R.drawable.ic_maneuver_roundabout
                ManeuverType.DEPART -> R.drawable.ic_maneuver_depart
                ManeuverType.ARRIVE -> R.drawable.ic_maneuver_arrive
                ManeuverType.STRAIGHT, ManeuverType.MERGE, ManeuverType.UNKNOWN -> R.drawable.ic_maneuver_straight
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<NavStep>() {
        override fun areItemsTheSame(oldItem: NavStep, newItem: NavStep): Boolean {
            return oldItem.startLocation == newItem.startLocation &&
                    oldItem.instruction == newItem.instruction &&
                    oldItem.distanceMeters == newItem.distanceMeters
        }

        override fun areContentsTheSame(oldItem: NavStep, newItem: NavStep): Boolean {
            return oldItem == newItem
        }
    }
}
