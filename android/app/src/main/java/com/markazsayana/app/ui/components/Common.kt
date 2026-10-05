package com.markazsayana.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.markazsayana.app.R
import com.markazsayana.app.ui.theme.BorderLight
import com.markazsayana.app.ui.theme.CardShape
import com.markazsayana.app.ui.theme.CardWhite
import com.markazsayana.app.ui.theme.ControlShape
import com.markazsayana.app.ui.theme.BrandNavy
import com.markazsayana.app.ui.theme.TextPrimary
import com.markazsayana.app.ui.theme.TextTertiary

const val CENTER_NAME = "الهندسية المتحدة"
const val CENTER_LOCATION = "الحدايق"

/** The center's original supplied artwork, displayed without altering the image. */
@Composable
fun BrandLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.brand_logo_original),
        contentDescription = "شعار $CENTER_NAME",
        contentScale = ContentScale.Fit,
        modifier = modifier.clip(CardShape).background(Color.Black),
    )
}

@Composable
fun BrandHeader(modifier: Modifier = Modifier, compact: Boolean = false, onDark: Boolean = false) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BrandLogo(Modifier.width(if (compact) 64.dp else 96.dp).height(if (compact) 46.dp else 68.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = CENTER_NAME,
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge,
                color = if (onDark) CardWhite else BrandNavy,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (compact) "$CENTER_LOCATION · إدارة الصيانة" else "مركز صيانة الأجهزة المنزلية · $CENTER_LOCATION",
                style = MaterialTheme.typography.bodySmall,
                color = if (onDark) CardWhite.copy(alpha = 0.78f) else TextTertiary,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun OutlinedCard(
    modifier: Modifier = Modifier,
    borderColor: Color = BorderLight,
    shape: androidx.compose.ui.graphics.Shape = CardShape,
    padding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScopeAlias.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CardWhite, shape)
            .border(1.dp, borderColor, shape)
            .padding(padding),
        content = content,
    )
}

typealias ColumnScopeAlias = androidx.compose.foundation.layout.ColumnScope

@Composable
fun AvatarCircle(
    initials: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    background: Color = BrandNavy.copy(alpha = 0.12f),
    contentColor: Color = BrandNavy,
) {
    Box(
        modifier = modifier
            .height(size)
            .width(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = initials, color = contentColor, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun StatusChip(
    text: String,
    background: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(text = text, color = contentColor, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = BrandNavy,
    contentColor: Color = CardWhite,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(ControlShape)
            .background(if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.5f))
            .clickable(enabled = enabled, role = Role.Button) { onClick() }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = contentColor, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = TextPrimary,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(ControlShape)
            .border(1.dp, com.markazsayana.app.ui.theme.BorderMedium, ControlShape)
            .clickable(role = Role.Button) { onClick() }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = contentColor, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
    }
}

@Composable
fun FullScreenLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = BrandNavy)
    }
}

@Composable
fun FullScreenError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "تعذّر تحميل البيانات", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = TextTertiary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
        PrimaryButton(text = "إعادة المحاولة", onClick = onRetry, modifier = Modifier.height(48.dp))
    }
}

@Composable
fun ComingSoonScreen(title: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Text(text = "هذه الشاشة قيد التطوير", style = MaterialTheme.typography.bodyMedium, color = TextTertiary, modifier = Modifier.padding(top = 6.dp))
    }
}
