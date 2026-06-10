package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.SystemLog
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.LocalAppDimens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Trang "Nhật ký hệ thống" — trước đây là Dialog cao 550dp, nay là TRANG riêng
 * (mở từ Cài đặt hệ thống) tận dụng toàn màn hình, đỡ chồng lớp.
 */
@Composable
fun LogsScreen(
    logs: List<SystemLog>,
    onClear: () -> Unit,
    onBack: () -> Unit
) {
    val dimens = LocalAppDimens.current
    val strings = LocalAppStrings.current
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    SubScreenScaffold(
        title = strings.logsTitle,
        onBack = onBack,
        backTag = "logs_back_button",
        actions = {
            OutlinedButton(
                onClick = onClear,
                border = BorderStroke(1.dp, Color(0xFFEF5350)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF5350)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(strings.logsClear, style = MaterialTheme.typography.labelMedium)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = dimens.screenPadding, vertical = dimens.itemSpacing),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = strings.logsDescription,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.fillMaxWidth()
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF0A0F1D), shape = RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.05f), shape = RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = strings.logsEmpty,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(logs) { log ->
                            val timeStr = dateFormat.format(Date(log.timestamp))
                            val levelColor = when (log.level) {
                                "ERROR" -> Color(0xFFEF5350)
                                "WARN" -> Color(0xFFFFB74D)
                                else -> Color(0xFF81C784)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "[$timeStr]",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Cyan
                                )
                                Text(
                                    text = log.level,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = levelColor,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(48.dp)
                                )
                                Text(
                                    text = log.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.LightGray,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
