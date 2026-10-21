package com.purrmatch.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography as MaterialTypography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.purrmatch.presentation.R

/** Values from Figma / Ии макет (264:5). This design currently defines a light palette only. */
object Colors {
    val Ink = Color(0xFF202447)
    val Body = Color(0xFF666D89)
    val Muted = Color(0xFF8589A2)
    val Accent = Color(0xFF7255DA)
    val Link = Color(0xFF7352AD)
    val FieldLabel = Color(0xFF68598C)
    val FieldBorder = Color(0xFFDED7F0)
    val Field = Color.White.copy(alpha = .68f)
    val ReadOnly = Color(0xFFEBE8F8)
    val SettingsReadOnly = Color(0xFFEAEAEA)
    val EnteredText = Color(0xFF34312C)
    val ReadOnlyLabel = Color(0xFF716786)
    val ReadOnlyText = Color(0xFF403958)
    val Surface = Color.White.copy(alpha = .78f)
    val IconSurface = Color(0xFFFFF9FF).copy(alpha = .75f)
    val Navigation = Color(0xFFFFFAFF).copy(alpha = .83f)
    val NavigationInactive = Color(0xFF8585A0)
    val NavigationActive = Color(0xFF7551BC)
    val NavigationSelection = Color(0xFFE6D4FF)
    val Lavender = Color(0xFFF1EAFF)
    val Cream = Color(0xFFFFFCF7)
    val Lilac = Color(0xFFF4EAFF)
    val MintGlow = Color(0xFFE9FFF5)
    val PinkGlow = Color(0xFFF4DCFF)
    val PrimaryStart = Color(0xFFFFF477)
    val PrimaryEnd = Color(0xFFFFCF82)
    val PublishStart = Color(0xFFFFD777)
    val PublishEnd = Color(0xFFFFB982)
    val PrimaryBorder = Color(0xFFFFEBBD)
    val PrimaryText = Color(0xFF54344C)
    val PrimaryStep = Color(0xFFDDA179)
    val Secondary = Color(0xFFE8DDFF)
    val SecondaryBorder = Color(0xFFFDF7FF)
    val SecondaryText = Color(0xFF6341A7)
    val Outline = Color(0xFFC8B4E8)
    val OutlineSurface = Color(0xFFFFF8FF).copy(alpha = .74f)
    val OutlineText = Color(0xFF7054AD)
    val Chip = Color.White.copy(alpha = .66f)
    val ChipText = Color(0xFF646187)
    val ChipSelected = Color(0xFFE6D6FF)
    val ChipSelectedBorder = Color(0xFFD3B6FA)
    val ChipSelectedText = Color(0xFF6241A0)
    val Tag = Color(0xFFF1E7FF)
    val TagText = Color(0xFF735E96)
    val Peach = Color(0xFFFFEADB)
    val PeachText = Color(0xFF955134)
    val Mint = Color(0xFFDEF4E9)
    val MintText = Color(0xFF416E59)
    val Danger = Color(0xFFFFE0E0)
    val DangerText = Color(0xFFAC0000)
    val Destructive = Color(0xFFF4CACA)
    val DestructiveText = Color(0xFFAD4561)
    val Notice = Color(0xFFF2E8FF).copy(alpha = .8f)
    val NoticeText = Color(0xFF6D548E)
    val PawDecoration = Color(0xFF8D629D)
    val Avatar = Color(0xFFE6D5FF)
    val Divider = Color(0xFFE9E7F4)
    val GroupLabel = Color(0xFF6C655E)
    val RadioBorder = Color(0xFFD7D8ED)
    val Scrim = Color(0xFF1D1B20).copy(alpha = .38f)
    val CardStep = Color(0xFFDDD0ED).copy(alpha = .4f)
    val ListStep = Color(0xFFE4D8F4).copy(alpha = .27f)
    val FieldShadow = Color(0xFF7C72AC).copy(alpha = .07f)
    val CardShadow = Color(0xFF83709C).copy(alpha = .11f)
    val IconShadow = Color(0xFF7771B3).copy(alpha = .09f)
    val ButtonShadow = Color(0xFFDAAA82).copy(alpha = .22f)
    val NavigationShadow = Color(0xFF8D6EB7).copy(alpha = .16f)
    val DialogShadow = Color(0xFF211D2B).copy(alpha = .24f)
    val Like = Color(0xFFC5F1CF)
    val LikeFade = Color(0xFFE2F4EA)
    val Skip = Color(0xFFF4C9C8)
    val SkipFade = Color(0xFFF7E7ED)
    val PageInactive = Color(0xFFBBBBBB)
    val SwipeIconSurface = Color(0xFFFFFAF8).copy(alpha = .7f)
}

object Spacing {
    val Screen = 20.dp
    val FieldGap = 12.dp
    val ChipGap = 7.dp
    val Section = 24.dp
}

object Typography {
    private val manrope = FontFamily(
        Font(R.font.manrope_bold, FontWeight.Bold),
        Font(R.font.manrope_extra_bold, FontWeight.ExtraBold),
    )
    private val nunito = FontFamily(
        Font(R.font.nunito_regular, FontWeight.Normal),
        Font(R.font.nunito_bold, FontWeight.Bold),
        Font(R.font.nunito_extra_bold, FontWeight.ExtraBold),
        Font(R.font.nunito_black, FontWeight.Black),
    )
    private val platform = PlatformTextStyle(includeFontPadding = false)
    val Hero = TextStyle(fontFamily = manrope, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, letterSpacing = (-1.2).sp, lineHeight = 38.4.sp, platformStyle = platform)
    val Name = TextStyle(fontFamily = manrope, fontWeight = FontWeight.ExtraBold, fontSize = 29.sp, letterSpacing = (-.6).sp, lineHeight = 34.8.sp, platformStyle = platform)
    val Title = TextStyle(fontFamily = manrope, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, letterSpacing = (-.6).sp, lineHeight = 26.25.sp, platformStyle = platform)
    val AppBar = Title.copy(letterSpacing = (-.8).sp, lineHeight = 25.2.sp)
    val Heading = TextStyle(fontFamily = manrope, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, letterSpacing = (-.6).sp, lineHeight = 20.sp, platformStyle = platform)
    val Button = TextStyle(fontFamily = manrope, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, letterSpacing = (-.2).sp, lineHeight = 19.sp, platformStyle = platform)
    val Body = TextStyle(fontFamily = nunito, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.7.sp, platformStyle = platform)
    val Subtitle = Body.copy(fontSize = 15.sp, lineHeight = 23.25.sp)
    val Field = TextStyle(fontFamily = nunito, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 19.sp, platformStyle = platform)
    val FieldLabel = TextStyle(fontFamily = manrope, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = .3.sp, lineHeight = 10.sp, platformStyle = platform)
    val ReadOnlyLabel = FieldLabel.copy(fontWeight = FontWeight.Bold, letterSpacing = .35.sp)
    val Caption = Body.copy(fontSize = 12.sp, lineHeight = 17.4.sp)
    val Tag = TextStyle(fontFamily = nunito, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, lineHeight = 16.sp, platformStyle = platform)
    val Chip = Tag.copy(fontSize = 14.sp, lineHeight = 19.sp)
    val GroupLabel = TextStyle(fontFamily = manrope, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, lineHeight = 16.sp, platformStyle = platform)
    val Navigation = Tag.copy(fontSize = 11.sp, lineHeight = 15.sp)
}

@Composable
fun Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Colors.Accent,
            onPrimary = Color.White,
            background = Colors.Lavender,
            onBackground = Colors.Ink,
            surface = Colors.Cream,
            onSurface = Colors.Ink,
            onSurfaceVariant = Colors.Body,
            error = Colors.DestructiveText,
        ),
        typography = MaterialTypography(
            headlineLarge = Typography.Hero,
            headlineMedium = Typography.Name,
            titleLarge = Typography.Title,
            titleMedium = Typography.Heading,
            bodyLarge = Typography.Body,
            bodyMedium = Typography.Field,
            bodySmall = Typography.Caption,
            labelLarge = Typography.Button,
            labelMedium = Typography.Tag,
            labelSmall = Typography.FieldLabel,
        ),
        content = content,
    )
}
