package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.LocalAppDimens

/**
 * Trang Cài đặt hệ thống — mở dạng TRANG riêng (thay thế hẳn UI chính, không phải dialog
 * chồng lên), để tránh chồng nhiều lớp giao diện gây nặng máy.
 * Chuỗi hiển thị lấy từ LocalAppStrings (đổi theo ngôn ngữ đã chọn ngay lập tức).
 */
@Composable
fun SettingsScreen(
    language: String,
    onLanguageChange: (String) -> Unit,
    onOpenInfo: () -> Unit,
    onOpenLogs: () -> Unit,
    onBack: () -> Unit
) {
    val dimens = LocalAppDimens.current
    val strings = LocalAppStrings.current

    SubScreenScaffold(
        title = strings.settingsTitle,
        onBack = onBack,
        backTag = "settings_back_button"
    ) { innerPadding ->
        // Màn lớn: giới hạn bề rộng nội dung để không bị dàn quá rộng trên tablet
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .padding(horizontal = dimens.screenPadding, vertical = dimens.itemSpacing),
                verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing)
            ) {
                // ---- Ngôn ngữ ----
                SettingsSectionCard(title = strings.sectionLanguage) {
                    LanguageOptionRow(
                        label = "Tiếng Việt",
                        flag = "🇻🇳",
                        selected = language != "en",
                        onClick = { onLanguageChange("vi") },
                        tag = "settings_lang_vi"
                    )
                    LanguageOptionRow(
                        label = "English",
                        flag = "🇬🇧",
                        selected = language == "en",
                        onClick = { onLanguageChange("en") },
                        tag = "settings_lang_en"
                    )
                    Text(
                        text = strings.languageDemoNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // ---- Chung ----
                SettingsSectionCard(title = strings.sectionGeneral) {
                    SettingsNavRow(
                        icon = Icons.Default.Info,
                        title = strings.appInfo,
                        subtitle = strings.appInfoSub,
                        onClick = onOpenInfo,
                        tag = "settings_open_info"
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    )
                    SettingsNavRow(
                        icon = Icons.Default.Build,
                        title = strings.systemLogs,
                        subtitle = strings.systemLogsSub,
                        onClick = onOpenLogs,
                        tag = "settings_open_logs"
                    )
                }

                // ---- Về ứng dụng ----
                SettingsSectionCard(title = strings.sectionAbout) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ArgbHslLogo(modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = "ARGB",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "HSL",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Light,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            Text(
                                text = "${strings.version} ${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
            modifier = Modifier.padding(start = 4.dp)
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun LanguageOptionRow(
    label: String,
    flag: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surface.copy(alpha = 0f)
            )
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = flag, style = MaterialTheme.typography.titleMedium)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        RadioButton(selected = selected, onClick = onClick)
    }
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }
        Text(
            text = "›",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}
