package io.github.nmohith22.vellurix

import android.graphics.Color

internal data class AppTheme(val id: String, val name: String, val background: Int, val surface: Int, val accent: Int, val text: Int, val muted: Int) {
    val dark: Boolean get() = (background shr 16 and 255) * 299 + (background shr 8 and 255) * 587 + (background and 255) * 114 < 128000
}

// Palette adapted from workout_app/lib/services/theme_service.dart.
internal val appThemes = listOf(
    AppTheme("system", "System Default", Color.BLACK, 0xFF1A1523.toInt(), 0xFFD93846.toInt(), -1, 0xFFAAA4AE.toInt()),
    AppTheme("quiet_light", "Quiet Luxury Light", 0xFFF2F2F7.toInt(), -1, 0xFFC05545.toInt(), 0xFF2C2C2E.toInt(), 0xFF6C6C70.toInt()),
    AppTheme("quiet_dark", "Quiet Luxury Dark", Color.BLACK, 0xFF1A1523.toInt(), 0xFFD93846.toInt(), -1, 0xFFAAA4AE.toInt()),
    AppTheme("carbon", "Carbon", Color.BLACK, 0xFF333333.toInt(), 0xFFF6C177.toInt(), 0xFFE0DEF4.toInt(), 0xFF908E9F.toInt()),
    AppTheme("serika", "Serika Dark", Color.BLACK, 0xFF2C2E31.toInt(), 0xFFE2B714.toInt(), 0xFFD1D0C5.toInt(), 0xFF646669.toInt()),
    AppTheme("nord", "Nord", Color.BLACK, 0xFF3B4252.toInt(), 0xFF88C0D0.toInt(), 0xFFECEFF4.toInt(), 0xFF4C566A.toInt()),
    AppTheme("sakura", "Sakura", 0xFFF5E6E8.toInt(), -1, 0xFFE88392.toInt(), 0xFF5F4B56.toInt(), 0xFFA58D9E.toInt()),
    AppTheme("cyberpunk", "Cyberpunk", Color.BLACK, 0xFF23253B.toInt(), 0xFFFF0055.toInt(), 0xFF00FFCC.toInt(), 0xFF6E6A86.toInt()),
    AppTheme("botanical", "Botanical", 0xFF7B9E89.toInt(), 0xFFEAF4EC.toInt(), 0xFF384F3D.toInt(), 0xFF1C2D20.toInt(), 0xFF5C7866.toInt()),
    AppTheme("laser", "Laser", Color.BLACK, 0xFF231E36.toInt(), 0xFF00E8C6.toInt(), 0xFFD91D81.toInt(), 0xFF5C5482.toInt()),
    AppTheme("modern_ink", "Modern Ink", 0xFFF7F7F7.toInt(), -1, Color.BLACK, 0xFF1A1A1A.toInt(), 0xFF7F7F7F.toInt()),
    AppTheme("terra", "Terra", Color.BLACK, 0xFF3A302C.toInt(), 0xFFDE8F6E.toInt(), 0xFFE6D5C3.toInt(), 0xFF736058.toInt()),
    AppTheme("matrix", "Matrix", Color.BLACK, 0xFF0D0D0D.toInt(), 0xFF15FF00.toInt(), 0xFF39FF14.toInt(), 0xFF008000.toInt()),
    AppTheme("red_dragon", "Red Dragon", Color.BLACK, 0xFF2B191B.toInt(), 0xFFFF3E3E.toInt(), 0xFFEDD2D2.toInt(), 0xFF634244.toInt()),
    AppTheme("dracula", "Dracula", Color.BLACK, 0xFF44475A.toInt(), 0xFFFF79C6.toInt(), 0xFFF8F8F2.toInt(), 0xFF6272A4.toInt()),
    AppTheme("gruvbox", "Gruvbox", Color.BLACK, 0xFF3C3836.toInt(), 0xFFFE8019.toInt(), 0xFFEBDBB2.toInt(), 0xFFA89984.toInt()),
    AppTheme("catppuccin", "Catppuccin", Color.BLACK, 0xFF313244.toInt(), 0xFFCBA6F7.toInt(), 0xFFCDD6F4.toInt(), 0xFF9399B2.toInt()),
    AppTheme("tokyo_night", "Tokyo Night", Color.BLACK, 0xFF24283B.toInt(), 0xFF7AA2F7.toInt(), 0xFFC0CAF5.toInt(), 0xFF565F89.toInt()),
    AppTheme("monokai", "Monokai", Color.BLACK, 0xFF3E3D32.toInt(), 0xFFF92672.toInt(), 0xFFF8F8F2.toInt(), 0xFF75715E.toInt()),
    AppTheme("ayu_dark", "Ayu Dark", Color.BLACK, 0xFF11151C.toInt(), 0xFFE6B450.toInt(), 0xFFBFBDB6.toInt(), 0xFF626A73.toInt()),
    AppTheme("rose_pine", "Rose Pine", Color.BLACK, 0xFF191724.toInt(), 0xFFEBBCBA.toInt(), 0xFFE0DEF4.toInt(), 0xFF908CAA.toInt()),
    AppTheme("everforest", "Everforest", Color.BLACK, 0xFF1E2326.toInt(), 0xFFA7C080.toInt(), 0xFFD3C6AA.toInt(), 0xFF859289.toInt()),
    AppTheme("ocean", "Ocean", Color.BLACK, 0xFF14232D.toInt(), 0xFF65B6C5.toInt(), 0xFFD8E7EF.toInt(), 0xFF78909C.toInt()),
    AppTheme("solarized_dark", "Solarized Dark", Color.BLACK, 0xFF002B36.toInt(), 0xFF2AA198.toInt(), 0xFFEEE8D5.toInt(), 0xFF839496.toInt()),
    AppTheme("matcha", "Matcha", Color.BLACK, 0xFF18211C.toInt(), 0xFF9BBF83.toInt(), 0xFFE0E8D8.toInt(), 0xFF879684.toInt()),
    AppTheme("lavender", "Lavender", Color.BLACK, 0xFF201B2C.toInt(), 0xFFB69CDB.toInt(), 0xFFE6DDF4.toInt(), 0xFF9287A5.toInt()),
    AppTheme("ember", "Ember", Color.BLACK, 0xFF211713.toInt(), 0xFFE78650.toInt(), 0xFFF1DDD1.toInt(), 0xFFA77B68.toInt()),
)

internal fun resolveAppTheme(id: String): AppTheme = appThemes.firstOrNull { it.id == id } ?: when (id) {
    "dark" -> appThemes.first { it.id == "quiet_dark" }
    "sepia" -> AppTheme("legacy_sepia", "Sepia", 0xFFF0E5D1.toInt(), 0xFFF8F1E4.toInt(), 0xFF9A5B45.toInt(), 0xFF493B2D.toInt(), 0xFF806E5D.toInt())
    else -> appThemes.first { it.id == "quiet_light" }
}
