package com.ferry.pet

/**
 * The module ids. [PetOptions] gates every optional capability through these,
 * and each module declares its own id from the same table, so an enable bit can
 * never drift away from the module it belongs to.
 */
object ModuleIds {
    const val WANDER = "wander"
    const val SPEECH = "speech"
    const val CLICK_SOUND = "clickSound"
    const val COMPANION = "companion"
    const val COLLISION = "collision"
    const val DIVINATION = "divination"

    val ALL = listOf(WANDER, SPEECH, CLICK_SOUND, COMPANION, COLLISION, DIVINATION)
}
