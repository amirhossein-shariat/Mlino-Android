package app.mlino.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mlino.R

val IranSans = FontFamily(
    Font(R.font.iransans_light, FontWeight.Light),
    Font(R.font.iransans_regular, FontWeight.Normal),
    Font(R.font.iransans_medium, FontWeight.Medium),
    Font(R.font.iransans_bold, FontWeight.Bold),
)

/** Semantic colours that Material's scheme has no slot for. */
data class Extra(
    val income: Color, val incomeBg: Color, val expense: Color, val expenseBg: Color, val warn: Color, val warnBg: Color,
    val heroA: Color, val heroB: Color, val heroFg: Color,
)

val LocalExtra = staticCompositionLocalOf {
    Extra(Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified,
        Color.Unspecified, Color.Unspecified, Color.Unspecified)
}

private val LightScheme = lightColorScheme(
    primary = Color(0xFF14304F), onPrimary = Color.White,
    secondary = Color(0xFF1F5C7A), onSecondary = Color.White,
    background = Color(0xFFEEF2F6), onBackground = Color(0xFF0B1F33),
    surface = Color.White, onSurface = Color(0xFF0B1F33),
    surfaceVariant = Color(0xFFF4F7FA), onSurfaceVariant = Color(0xFF6A7B8C),
    outline = Color(0xFFE1E8EF), outlineVariant = Color(0xFFE1E8EF),
    error = Color(0xFFC93B3B),
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7FD1AE), onPrimary = Color(0xFF07131C),
    secondary = Color(0xFF6FB6D6), onSecondary = Color(0xFF07131C),
    background = Color(0xFF070D14), onBackground = Color(0xFFE8EEF4),
    surface = Color(0xFF101A26), onSurface = Color(0xFFE8EEF4),
    surfaceVariant = Color(0xFF16222F), onSurfaceVariant = Color(0xFF8FA1B3),
    outline = Color(0xFF1E2D3D), outlineVariant = Color(0xFF1E2D3D),
    error = Color(0xFFFF8A85),
)
private val LightExtra = Extra(Color(0xFF12805C), Color(0xFFDDF3EA), Color(0xFFC93B3B), Color(0xFFFBE4E2), Color(0xFFA8620B), Color(0xFFFCEFD6),
    Color(0xFF14304F), Color(0xFF1B6B6B), Color.White)
private val DarkExtra = Extra(Color(0xFF5FD3A2), Color(0xFF12342A), Color(0xFFFF8A85), Color(0xFF3A1E1F), Color(0xFFF2B35E), Color(0xFF3A2D14),
    Color(0xFF173A5C), Color(0xFF16665F), Color.White)

private fun Typography.withFont(f: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = f), displayMedium = displayMedium.copy(fontFamily = f), displaySmall = displaySmall.copy(fontFamily = f),
    headlineLarge = headlineLarge.copy(fontFamily = f), headlineMedium = headlineMedium.copy(fontFamily = f), headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f, fontWeight = FontWeight.Bold), titleMedium = titleMedium.copy(fontFamily = f, fontWeight = FontWeight.Bold),
    titleSmall = titleSmall.copy(fontFamily = f, fontWeight = FontWeight.Medium),
    bodyLarge = bodyLarge.copy(fontFamily = f), bodyMedium = bodyMedium.copy(fontFamily = f, fontSize = 14.sp), bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f), labelMedium = labelMedium.copy(fontFamily = f), labelSmall = labelSmall.copy(fontFamily = f),
)

@Composable
fun MlinoTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalExtra provides (if (dark) DarkExtra else LightExtra),
        LocalLayoutDirection provides LayoutDirection.Rtl,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = Typography().withFont(IranSans),
            shapes = Shapes(
                extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(14.dp), medium = RoundedCornerShape(20.dp),
                large = RoundedCornerShape(26.dp), extraLarge = RoundedCornerShape(32.dp),
            ),
            content = content,
        )
    }
}

val extra: Extra @Composable get() = LocalExtra.current
