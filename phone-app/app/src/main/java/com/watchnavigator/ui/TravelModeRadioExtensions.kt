package com.watchnavigator.ui

import android.widget.RadioGroup
import com.watchnavigator.R
import com.watchnavigator.model.TravelMode

fun RadioGroup.checkTravelMode(mode: TravelMode) {
    check(if (mode == TravelMode.WALKING) R.id.rbWalking else R.id.rbDriving)
}

fun RadioGroup.selectedTravelMode(): TravelMode = if (checkedRadioButtonId == R.id.rbWalking) TravelMode.WALKING else TravelMode.DRIVING
