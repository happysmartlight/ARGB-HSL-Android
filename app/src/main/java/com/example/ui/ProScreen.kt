package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.billing.ProSubscriptionManager
import com.example.billing.ProSubscriptionState
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.CyberYellow
import com.example.ui.theme.LocalAppDimens
import com.example.ui.theme.NeonMagenta

/**
 * Trang "ARGB HSL Pro" — trước đây là Dialog, nay là TRANG riêng (thay thế UI chính
 * khi mở) để tránh chồng lớp giao diện gây nặng máy.
 *
 * Thiết kế "premium" theo chất neon-cyber của app: hero gradient + chữ PRO ánh kim,
 * thẻ giá viền neon, 6 feature card mô tả quyền lợi, CTA dùng NeonActionButton.
 */
@Composable
fun ProScreen(
    state: ProSubscriptionState,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    onRefresh: () -> Unit,
    onManageSubscription: () -> Unit,
    onDebugToggle: () -> Unit,
    onBack: () -> Unit
) {
    val dimens = LocalAppDimens.current
    val strings = LocalAppStrings.current

    SubScreenScaffold(
        title = if (state.isPro) strings.proTitleActive else strings.proTitleUpgrade,
        onBack = onBack,
        backTag = "pro_back_button"
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
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .padding(horizontal = dimens.screenPadding, vertical = dimens.itemSpacing),
                verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing)
            ) {
                ProHeroCard(isPro = state.isPro, tagline = strings.proHeroTagline, activeBadge = strings.proBadgeActive)

                ProPriceCard(
                    planLabel = strings.proYearlyPlan,
                    priceText = state.priceText,
                    renewNote = strings.proAutoRenewNote
                )

                // ---- Quyền lợi Pro ----
                Text(
                    text = strings.proWhatYouGet,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = CyberCyan,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )

                ProFeatureCard(Icons.Default.PlayArrow, CyberCyan, strings.proFeat1Title, strings.proFeat1Desc)
                ProFeatureCard(Icons.Default.Edit, NeonMagenta, strings.proFeat2Title, strings.proFeat2Desc)
                ProFeatureCard(Icons.Default.Send, CyberYellow, strings.proFeat3Title, strings.proFeat3Desc)
                ProFeatureCard(Icons.Default.AddCircle, Color(0xFF8B5CF6), strings.proFeat4Title, strings.proFeat4Desc)
                ProFeatureCard(Icons.Default.List, CyberGreen, strings.proFeat5Title, strings.proFeat5Desc)
                ProFeatureCard(Icons.Default.Delete, Color(0xFFFF8A3D), strings.proFeat6Title, strings.proFeat6Desc)

                // ---- Thông báo trạng thái billing ----
                val messageColor = if (state.errorMessage != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                }
                val messageText = state.errorMessage ?: state.statusMessage
                if (!messageText.isNullOrBlank()) {
                    Text(
                        text = messageText,
                        style = MaterialTheme.typography.bodySmall,
                        color = messageColor,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                if (state.isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                // ---- CTA chính ----
                if (state.isPro) {
                    ProActiveBanner(text = strings.proTitleActive)
                } else {
                    val canBuy = state.canBuy && !state.isLoading
                    NeonActionButton(
                        text = strings.proBuy,
                        icon = Icons.Default.ShoppingCart,
                        neonColor = CyberYellow,
                        onClick = { if (canBuy) onPurchase() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (canBuy) 1f else 0.45f)
                            .testTag("pro_buy_button")
                    )
                }

                // ---- Hàng phụ: khôi phục / billing / quản lý ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onRestore,
                        enabled = !state.isLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.proRestore)
                    }
                    OutlinedButton(
                        onClick = onManageSubscription,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.proManageOnPlay)
                    }
                }
                TextButton(
                    onClick = onRefresh,
                    enabled = !state.isLoading,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(strings.proReloadBilling)
                }

                if (BuildConfig.DEBUG) {
                    OutlinedButton(
                        onClick = onDebugToggle,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (state.isPro) strings.proDebugOff else strings.proDebugOn)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/** Hero: nền gradient tím→xanh sâu, ngôi sao phát sáng, chữ PRO ánh kim. */
@Composable
private fun ProHeroCard(isPro: Boolean, tagline: String, activeBadge: String) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF2A1059), Color(0xFF131334), Color(0xFF06283A))
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(CyberYellow.copy(alpha = 0.55f), NeonMagenta.copy(alpha = 0.45f), CyberCyan.copy(alpha = 0.55f))
                ),
                shape = shape
            )
            .padding(vertical = 26.dp, horizontal = 20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Ngôi sao trong huy hiệu tròn phát sáng vàng
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .shadow(elevation = 18.dp, shape = CircleShape, spotColor = CyberYellow, ambientColor = CyberYellow)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFF3D2E00), Color(0xFF1A1404)))
                    )
                    .border(1.dp, CyberYellow.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = CyberYellow,
                    modifier = Modifier.size(34.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "ARGB HSL",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Light,
                    color = Color.White,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "PRO",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        brush = Brush.horizontalGradient(listOf(CyberYellow, Color(0xFFFFB347), NeonMagenta))
                    ),
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp
                )
            }

            if (isPro) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = CyberGreen.copy(alpha = 0.14f),
                    contentColor = CyberGreen,
                    border = BorderStroke(1.dp, CyberGreen.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text(activeBadge, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
                    }
                }
            }

            Text(
                text = tagline,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Thẻ giá: viền gradient neon, giá nổi bật. */
@Composable
private fun ProPriceCard(planLabel: String, priceText: String, renewNote: String) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(CyberCyan.copy(alpha = 0.6f), NeonMagenta.copy(alpha = 0.5f))
                ),
                shape = shape
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = planLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = priceText,
                style = MaterialTheme.typography.headlineSmall.copy(
                    brush = Brush.horizontalGradient(listOf(CyberCyan, Color(0xFF7DF9FF)))
                ),
                fontWeight = FontWeight.Black
            )
        }
        Text(
            text = renewNote,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

/** Feature card: icon neon trong ô màu riêng + tiêu đề + mô tả. */
@Composable
private fun ProFeatureCard(
    icon: ImageVector,
    neonColor: Color,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(neonColor.copy(alpha = 0.12f))
                .border(1.dp, neonColor.copy(alpha = 0.45f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = neonColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f)
            )
        }
    }
}

/** Banner hiển thị khi đã là Pro (thay nút mua). */
@Composable
private fun ProActiveBanner(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CyberGreen.copy(alpha = 0.10f))
            .border(1.dp, CyberGreen.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CyberGreen, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = CyberGreen
        )
    }
}

/** Panel khoá tính năng Pro hiển thị inline trong các tab (không phải dialog). */
@Composable
fun ProLockedFeaturePanel(
    state: ProSubscriptionState,
    title: String,
    description: String,
    onUpgrade: () -> Unit,
    onRestore: () -> Unit
) {
    val dimens = LocalAppDimens.current
    val strings = LocalAppStrings.current

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pro_locked_panel")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.cardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(dimens.iconSize)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                textAlign = TextAlign.Center
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Text(
                    text = state.priceText,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onRestore,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.proRestore)
                }
                Button(
                    onClick = onUpgrade,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.proUpgradeShort)
                }
            }
        }
    }
}

fun openPlaySubscriptionCenter(context: Context) {
    val uri = Uri.parse(
        "https://play.google.com/store/account/subscriptions" +
            "?sku=${ProSubscriptionManager.PRO_PRODUCT_ID}&package=${context.packageName}"
    )
    val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure {
            android.widget.Toast
                .makeText(context, "Không mở được trang quản lý subscription.", android.widget.Toast.LENGTH_LONG)
                .show()
        }
}

fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
