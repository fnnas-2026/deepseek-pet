package com.ferry.pet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ferry.pet.R

/** 协议类型，用于区分三份文本。 */
enum class AgreementType { USER, PRIVACY, SECURITY }

/** 协议正文页：分段纯文本，纵向滚动。 */
@Composable
fun AgreementPage(type: AgreementType) {
    val context = LocalContext.current
    val sections = remember(type) { AgreementContent.sections(context, type) }
    Column(Modifier.fillMaxWidth()
        .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        sections.forEachIndexed { index, paragraph ->
            val isHeading = index % 2 == 0
            Text(paragraph,
                color = if (isHeading) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = if (isHeading) 15.sp else 14.sp,
                    lineHeight = 22.sp))
        }
    }
}

/** 关于页内的协议入口卡片。 */
@Composable
fun AgreementEntries(onOpen: (AgreementType) -> Unit) {
    MenuCardEntry(R.string.agreement_user, R.string.agreement_user_desc) { onOpen(AgreementType.USER) }
    MenuCardEntry(R.string.agreement_privacy, R.string.agreement_privacy_desc) { onOpen(AgreementType.PRIVACY) }
    MenuCardEntry(R.string.agreement_security, R.string.agreement_security_desc) { onOpen(AgreementType.SECURITY) }
}

@Composable
private fun MenuCardEntry(titleRes: Int, descRes: Int, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(descRes), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.headlineSmall)
    }
}
