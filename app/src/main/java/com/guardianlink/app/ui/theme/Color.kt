package com.guardianlink.app.ui.theme

import androidx.compose.ui.graphics.Color

// Brand — calm, trustworthy
val PrimaryBlue = Color(0xFF2563EB)
val SecondaryBlue = Color(0xFF3B82F6)
val LightBlue = Color(0xFF60A5FA)
val DarkBlue = Color(0xFF1E3A8A)

// Emergency — reserved ONLY for SOS/alert states
val AlertRed = Color(0xFFDC2626)
val AlertRedDark = Color(0xFF991B1B)
val WarningAmber = Color(0xFFF59E0B)

// Status
val OnlineGreen = Color(0xFF22C55E)
val OfflineGray = Color(0xFF94A3B8)

// Legacy surfaces maintained for compatibility
val Background = Color(0xFFF1F5F9)
val CardBackground = Color(0xFFFFFFFF)
val CardColor = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFF0F172A)
val TextSecondary = Color(0xFF64748B)
val Divider = Color(0xFFE2E8F0)

val BackgroundDark = Color(0xFF090B13)
val CardBackgroundDark = Color(0xFF131828)
val TextPrimaryDark = Color(0xFFF8FAFC)
val TextSecondaryDark = Color(0xFF94A3B8)
val DividerDark = Color(0xFF1E293B)

// Accents
val HeroGradientStart = Color(0xFF1D4ED8)
val HeroGradientMid = Color(0xFF2563EB)
val HeroGradientEnd = Color(0xFF3B82F6)
val EmeraldGreen = Color(0xFF10B981)
val PurpleAccent = Color(0xFF8B5CF6)
val AmberAccent = Color(0xFFF59E0B)
val CyanAccent = Color(0xFF06B6D4)
val PulseRingColor = Color(0x44DC2626)
val PulseRingOuterColor = Color(0x22DC2626)

// Neumorphic / Cosmic aliases
val CosmicNavyDark = Color(0xFF0A0C16)
val CosmicIndigoSurface = Color(0xFF131728)
val CosmicCardElevated = Color(0xFF1A2139)
val CosmicBorderHighlight = Color(0x338A95D6)
val ClayCanvasLight = Color(0xFFE2E8F0)
val PorcelainWhite = Color(0xFFFFFFFF)
val ClayTileSurface = Color(0xFFF1F5F9)
val ClayBorderHighlight = Color(0x99FFFFFF)
val ClayShadowAmbient = Color(0x1F7A8BB0)
val ClayShadowSpot = Color(0x2863749B)

// Neon & Pastel Highlights
val ElectricBlueAccent = Color(0xFF4F75FF)
val NeonCyanAccent = Color(0xFF00E5FF)
val GlowingMint = Color(0xFF00F5A0)
val CoralEmergency = Color(0xFFFF3B5C)
val PastelPurple = Color(0xFF9D65FF)
val PastelOrange = Color(0xFFFF954F)
val PastelPink = Color(0xFFFF6584)
val PastelYellow = Color(0xFFFFB800)

// =========================================================================
// LIQUID GLASS UI KIT DESIGN TOKENS (Matches Reference Design System)
// =========================================================================

// Backgrounds
val LiquidCanvasDark = Color(0xFF090B14)
val LiquidCanvasLight = Color(0xFFF1F2F8) // Soft studio pearlescent canvas matching reference photo
val LiquidSurfaceDark = Color(0xFF111628)
val LiquidSurfaceLight = Color(0xFFFFFFFF)

// Translucent Frosted Glass Slabs (multi-opacity layers)
val LiquidGlassBgDark = Color(0x40161F36)
val LiquidGlassBgDarkElevated = Color(0x661A2440)
val LiquidGlassBgLight = Color(0xB2FFFFFF) // Frosted acrylic translucent white
val LiquidGlassBgLightElevated = Color(0xDAFFFFFF)

// Prismatic Chromatic Dispersion Glints (matching rainbow edge refractions in reference photo)
val LiquidPrismaticCyan = Color(0xFF00E5FF)
val LiquidPrismaticMagenta = Color(0xFFFF2E93)
val LiquidPrismaticViolet = Color(0xFF8B5CF6)
val LiquidPrismaticAmber = Color(0xFFFFB800)

// Specular Glass Edge Highlights & Neon Rims
val LiquidGlassBorderLight = Color(0x99FFFFFF)
val LiquidGlassBorderDark = Color(0x33A0B8FF)
val LiquidGlassBorderHighlight = Color(0xCCFFFFFF)
val LiquidGlassRimCyan = Color(0x6600E5FF)
val LiquidGlassRimPink = Color(0x66FF2E93)
val LiquidGlassRimPurple = Color(0x668B5CF6)

// Specular reflection gradient stops
val GlassSpecularTop = Color(0x55FFFFFF)
val GlassSpecularBottom = Color(0x05FFFFFF)

// Ambient Caustic Glow Orbs (for radiant backdrops)
val LiquidCausticCyan = Color(0x2800E5FF)
val LiquidCausticPurple = Color(0x288B5CF6)
val LiquidCausticMagenta = Color(0x22FF2E93)
val LiquidCausticBlue = Color(0x282563EB)

// Liquid Jelly & Neon Core Accents
val LiquidNeonCyan = Color(0xFF00E5FF)
val LiquidNeonPurple = Color(0xFF8B5CF6)
val LiquidNeonViolet = Color(0xFF7C3AED)
val LiquidNeonMagenta = Color(0xFFFF2E93)
val LiquidNeonPink = Color(0xFFFF529A)
val LiquidNeonGreen = Color(0xFF00F5A0)
val LiquidSunsetAmber = Color(0xFFFF7A45)
val LiquidHoneyGold = Color(0xFFFFB020)
val LiquidRubyEmergency = Color(0xFFFF2A55)
val LiquidRubyDark = Color(0xFF991B1B)

// Liquid Gradient Presets
val LiquidGradientPrimary = listOf(Color(0xFF8B5CF6), Color(0xFF6366F1))
val LiquidGradientSecondary = listOf(Color(0xFF06B6D4), Color(0xFF14B8A6))
val LiquidGradientCyanGlow = listOf(Color(0xFF00E5FF), Color(0xFF0099FF))
val LiquidGradientSunset = listOf(Color(0xFFFF7A45), Color(0xFFFFB020))
val LiquidGradientMint = listOf(Color(0xFF00F5A0), Color(0xFF00D2FF))
val LiquidGradientMagenta = listOf(Color(0xFFFF2E93), Color(0xFF8B5CF6))
val LiquidGradientEmergency = listOf(Color(0xFFFF2A55), Color(0xFFDC2626), Color(0xFF991B1B))
val LiquidGradientGlassWhite = listOf(Color(0x66FFFFFF), Color(0x1AFFFFFF))