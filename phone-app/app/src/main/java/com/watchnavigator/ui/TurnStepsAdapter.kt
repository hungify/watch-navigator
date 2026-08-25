package com.watchnavigator.ui

import android.graphics.Color
import android.util.TypedValue
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
    private var activeStepIndex: Int = -1
    private var activeStepRemainingDistance: Int? = null

    fun setActiveStep(
        index: Int,
        remainingDistanceMeters: Int? = null
    ) {
        val prevIndex = activeStepIndex
        activeStepIndex = index
        activeStepRemainingDistance = remainingDistanceMeters

        if (prevIndex in 0 until itemCount) {
            notifyItemChanged(prevIndex)
        }
        if (activeStepIndex in 0 until itemCount) {
            notifyItemChanged(activeStepIndex)
        }
    }

    fun clearActiveStep() {
        val prevIndex = activeStepIndex
        activeStepIndex = -1
        activeStepRemainingDistance = null
        if (prevIndex in 0 until itemCount) {
            notifyItemChanged(prevIndex)
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding =
            ItemNavStepBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        holder.bind(getItem(position), position == activeStepIndex, activeStepRemainingDistance)
    }

    inner class ViewHolder(
        private val binding: ItemNavStepBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            step: NavStep,
            isActive: Boolean,
            activeDistance: Int?
        ) {
            binding.tvStreetName.text = step.streetName.ifBlank { step.instruction }
            binding.tvInstruction.text = step.instruction

            val displayDistance =
                if (isActive && activeDistance != null) {
                    activeDistance
                } else {
                    step.distanceMeters
                }
            binding.tvDistance.text = DistanceFormatter.formatDistance(displayDistance)
            binding.ivManeuver.setImageResource(getManeuverIcon(step.maneuver))

            if (isActive) {
                val typedValue = TypedValue()
                val theme = binding.root.context.theme
                if (theme.resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer, typedValue, true)) {
                    binding.root.setBackgroundColor(typedValue.data)
                } else {
                    binding.root.setBackgroundColor(Color.parseColor("#E0F2FE"))
                }
            } else {
                binding.root.setBackgroundColor(Color.TRANSPARENT)
            }
        }

        @DrawableRes
        private fun getManeuverIcon(maneuver: ManeuverType): Int =
            when (maneuver) {
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

    private object DiffCallback : DiffUtil.ItemCallback<NavStep>() {
        override fun areItemsTheSame(
            oldItem: NavStep,
            newItem: NavStep
        ): Boolean =
            oldItem.startLocation == newItem.startLocation &&
                oldItem.instruction == newItem.instruction &&
                oldItem.distanceMeters == newItem.distanceMeters

        override fun areContentsTheSame(
            oldItem: NavStep,
            newItem: NavStep
        ): Boolean = oldItem == newItem
    }
}
