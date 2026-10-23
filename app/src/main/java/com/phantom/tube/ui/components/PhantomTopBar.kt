package com.phantom.tube.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantom.tube.R
import com.phantom.tube.core.theme.StatusBarBezelBorderCenter
import com.phantom.tube.core.theme.StatusBarBezelBorderEdge
import com.phantom.tube.core.theme.SystemStatusBarColor
import com.phantom.tube.core.theme.TextPrimary

private val STATUS_BAR_CORNER_RADIUS = 16.dp
private val STATUS_BAR_MIN_HEIGHT = 24.dp
private val STATUS_BAR_CUTOUT_THRESHOLD = 30.dp
private val STATUS_BAR_CUTOUT_TRIM = 4.dp
private val STATUS_BAR_MEDIUM_TRIM = 2.dp
private val STATUS_BAR_BORDER_WIDTH = 1.dp

/**
 * Strip status bar hitam dengan sudut bawah kiri & kanan melengkung (Curved Status Bar Bezel).
 * Menyesuaikan tinggi secara adaptif di semua perangkat (punch-hole, notch, maupun layar standar)
 * agar tetap ramping tanpa memotong indikator sistem.
 */
@Composable
fun PhantomStatusBarStrip(
    modifier: Modifier = Modifier
) {
    val rawTopInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val adaptiveHeight = when {
        rawTopInset >= STATUS_BAR_CUTOUT_THRESHOLD -> (rawTopInset - STATUS_BAR_CUTOUT_TRIM).coerceAtLeast(STATUS_BAR_MIN_HEIGHT)
        rawTopInset > STATUS_BAR_MIN_HEIGHT -> (rawTopInset - STATUS_BAR_MEDIUM_TRIM).coerceAtLeast(STATUS_BAR_MIN_HEIGHT)
        else -> rawTopInset.coerceAtLeast(STATUS_BAR_MIN_HEIGHT)
    }
    val shape = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 0.dp,
        bottomStart = STATUS_BAR_CORNER_RADIUS,
        bottomEnd = STATUS_BAR_CORNER_RADIUS
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(adaptiveHeight)
            .clip(shape)
            .background(SystemStatusBarColor)
            .drawWithCache {
                val strokePx = STATUS_BAR_BORDER_WIDTH.toPx()
                val halfStroke = strokePx / 2f
                val w = size.width
                val h = size.height
                val r = STATUS_BAR_CORNER_RADIUS.toPx().coerceAtMost((h - strokePx).coerceAtLeast(0f))

                val contourPath = Path().apply {
                    moveTo(halfStroke, (h - r).coerceAtLeast(0f))
                    arcTo(
                        rect = Rect(
                            left = halfStroke,
                            top = h - 2f * r,
                            right = 2f * r - halfStroke,
                            bottom = h - halfStroke
                        ),
                        startAngleDegrees = 180f,
                        sweepAngleDegrees = -90f,
                        forceMoveTo = false
                    )
                    lineTo(w - r, h - halfStroke)
                    arcTo(
                        rect = Rect(
                            left = w - 2f * r + halfStroke,
                            top = h - 2f * r,
                            right = w - halfStroke,
                            bottom = h - halfStroke
                        ),
                        startAngleDegrees = 90f,
                        sweepAngleDegrees = -90f,
                        forceMoveTo = false
                    )
                }

                val borderBrush = Brush.horizontalGradient(
                    colors = listOf(
                        StatusBarBezelBorderEdge,
                        StatusBarBezelBorderCenter,
                        StatusBarBezelBorderEdge
                    )
                )

                onDrawBehind {
                    drawPath(
                        path = contourPath,
                        brush = borderBrush,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }
            }
    )
}

/**
 * Top App Bar beranda:
 * - Logo Phantom & nama aplikasi
 * - Tombol Segarkan, Pengaturan & Cari
 * - Filter chip kategori
 */
@Composable
fun PhantomTopBar(
    selectedCategory: String,
    categories: List<String>,
    onCategorySelected: (String) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
    ) {
        PhantomStatusBarStrip()

        // Baris Atas: Logo & Aksi
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Logo & Judul
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "Logo Phantom",
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "PHANTOM",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
            }

            // Aksi: Settings + Cari
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (onSettingsClick != null) {
                    PhantomIconButton(
                        icon = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.nav_settings),
                        size = 38.dp,
                        iconSize = 20.dp,
                        onClick = onSettingsClick
                    )
                }

                PhantomIconButton(
                    icon = Icons.Default.Search,
                    contentDescription = stringResource(R.string.nav_search),
                    size = 38.dp,
                    iconSize = 22.dp,
                    onClick = onSearchClick
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Baris Kategori
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { cat ->
                val localizedCat = when (cat) {
                    "Semua" -> stringResource(R.string.cat_all)
                    "Trending" -> stringResource(R.string.cat_trending)
                    "Musik" -> stringResource(R.string.cat_music)
                    "Gaming" -> stringResource(R.string.cat_gaming)
                    "Berita" -> stringResource(R.string.cat_news)
                    "Podcast" -> stringResource(R.string.cat_podcast)
                    "Teknologi" -> stringResource(R.string.cat_tech)
                    "Animasi" -> stringResource(R.string.cat_animation)
                    else -> cat
                }
                PhantomChip(
                    text = localizedCat,
                    isSelected = cat == selectedCategory,
                    onClick = { onCategorySelected(cat) }
                )
            }
        }
    }
}
