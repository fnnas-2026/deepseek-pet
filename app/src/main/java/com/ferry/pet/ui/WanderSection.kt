package com.ferry.pet.ui

import androidx.compose.runtime.Composable
import com.ferry.pet.PetOptions
import com.ferry.pet.R

/** The wander module's one switch; switching it off parks every pet in its idle loop. */
@Composable
fun WanderSection(options: PetOptions, onChanged: (PetOptions) -> Unit) {
    SettingSwitch(R.string.wander, options.wander) { onChanged(options.copy(wander = it)) }
}
