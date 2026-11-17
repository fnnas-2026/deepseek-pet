package com.ferry.pet.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ferry.pet.PetOptions
import com.ferry.pet.R

/** 每日一签：模块开关；开启后长按桌宠菜单可「抽支签」，同一天结果固定。 */
@Composable
fun DivinationSection(options: PetOptions, onChanged: (PetOptions) -> Unit) {
    SettingSwitch(R.string.module_divination, options.divination) {
        onChanged(options.copy(divination = it))
    }
    Text(stringResource(R.string.divination_hint))
}
