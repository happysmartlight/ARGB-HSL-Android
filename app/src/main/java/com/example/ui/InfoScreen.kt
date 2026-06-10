package com.example.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.LocalAppDimens

/**
 * Trang "Thông tin ứng dụng" — trước đây là Dialog, nay là TRANG riêng
 * (mở từ Cài đặt hệ thống) để tránh chồng lớp giao diện.
 */
@Composable
fun InfoScreen(
    wifiSsid: String,
    wifiSignal: String,
    deviceIp: String,
    onBack: () -> Unit
) {
    val dimens = LocalAppDimens.current
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val openZalo = {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://zalo.me/1321084611356589870"))
            )
        }
        Unit
    }

    SubScreenScaffold(
        title = strings.infoTitle,
        onBack = onBack,
        backTag = "info_back_button"
    ) { innerPadding ->
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
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ArgbHslLogo(modifier = Modifier.size(72.dp))

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "ARGB",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "HSL",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Light,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        text = "Happy Smart Light",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }

                InfoScreenDivider()

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InfoScreenRow(
                        label = strings.infoVersionLabel,
                        value = "v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE}) Premium VN"
                    )
                    InfoScreenRow(label = strings.infoManufacturer, value = "Happy Smart Light")
                    InfoScreenRow(
                        label = strings.infoWebsite,
                        value = "happysmartlight.com",
                        valueColor = MaterialTheme.colorScheme.primary
                    )

                    // --- Kênh Zalo OA (QR bấm được) ---
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = strings.infoZaloScan,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Image(
                            painter = painterResource(com.example.R.drawable.zalo_oa_qr),
                            contentDescription = "QR Zalo OA Happy Smart Light",
                            modifier = Modifier
                                .size(168.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = openZalo)
                        )
                        Text(
                            text = strings.infoOpenZalo,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable(onClick = openZalo)
                        )
                    }

                    InfoScreenDivider()

                    Text(
                        text = strings.infoConnectionSection,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )

                    InfoScreenRow(label = strings.infoNetwork, value = wifiSsid)
                    InfoScreenRow(label = strings.infoSignal, value = wifiSignal)
                    InfoScreenRow(
                        label = strings.infoDeviceIp,
                        value = deviceIp,
                        valueColor = MaterialTheme.colorScheme.primary
                    )
                }

                InfoScreenDivider()

                Text(
                    text = strings.infoTagline,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun InfoScreenDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    )
}

@Composable
private fun InfoScreenRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}
