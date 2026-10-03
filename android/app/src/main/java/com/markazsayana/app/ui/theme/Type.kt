package com.markazsayana.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.markazsayana.app.R

val IbmPlexSansArabic = FontFamily(
    Font(R.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_arabic_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_arabic_bold, FontWeight.Bold),
)

private fun style(size: Int, weight: FontWeight, lineHeight: Int? = null) = TextStyle(
    fontFamily = IbmPlexSansArabic,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (lineHeight ?: (size * 1.3).toInt()).sp,
    textAlign = TextAlign.Start,
)

// Sizes mirror the design's inline font-size values (40/26/24/22/18/17/15/14/13/12/11).
val Display40Bold = style(40, FontWeight.Bold, 48)
val Headline26Bold = style(26, FontWeight.Bold, 32)
val Title24Bold = style(24, FontWeight.Bold, 30)
val Title22Bold = style(22, FontWeight.Bold, 28)
val Title18Bold = style(18, FontWeight.Bold, 24)
val Title17Bold = style(17, FontWeight.Bold, 24)
val Body15SemiBold = style(15, FontWeight.SemiBold, 22)
val Body15 = style(15, FontWeight.Normal, 24)
val Body14SemiBold = style(14, FontWeight.SemiBold, 20)
val Body14 = style(14, FontWeight.Normal, 20)
val Caption13SemiBold = style(13, FontWeight.SemiBold, 18)
val Caption13 = style(13, FontWeight.Normal, 18)
val Caption12SemiBold = style(12, FontWeight.SemiBold, 16)
val Caption12 = style(12, FontWeight.Normal, 16)
val Micro11 = style(11, FontWeight.Normal, 14)
