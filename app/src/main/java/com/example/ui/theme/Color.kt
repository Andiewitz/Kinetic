package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ── KINETIC — DESIGN SYSTEM TOKENS ──

// Base
val KineticBackground = Color(0xFF060606) // near-black, not pure #000
val KineticForeground = Color(0xFFFFFFFF)

// Text hierarchy (white with opacity layers)
val KineticTextPrimary = Color(0xFFFFFFFF)              // primary: 100%
val KineticTextSecondary = Color(0x7AFFFFFF)            // secondary: 48% (rgba(255, 255, 255, 0.48))
val KineticTextTertiary = Color(0x57FFFFFF)             // tertiary: 34% (rgba(255, 255, 255, 0.34))
val KineticTextMuted = Color(0x38FFFFFF)                // muted: 22% (rgba(255, 255, 255, 0.22))
val KineticTextGhost = Color(0x29FFFFFF)                // ghost: 16% (rgba(255, 255, 255, 0.16))
val KineticTextDimmed = Color(0x42FFFFFF)               // dimmed: 26% (rgba(255, 255, 255, 0.26))

// Borders
val KineticBorderSubtle = Color(0x0EFFFFFF)             // subtle: 5.5% (rgba(255, 255, 255, 0.055))
val KineticBorderSoft = Color(0x12FFFFFF)               // soft: 7% (rgba(255, 255, 255, 0.07))
val KineticBorderMedium = Color(0x1AFFFFFF)             // medium: 10% (rgba(255, 255, 255, 0.10))
val KineticBorderStrong = Color(0x21FFFFFF)             // strong: 13% (rgba(255, 255, 255, 0.13))
val KineticBorderHover = Color(0x29FFFFFF)              // hover: 16% (rgba(255, 255, 255, 0.16))
val KineticBorderActive = Color(0x2EFFFFFF)             // active: 18% (rgba(255, 255, 255, 0.18))

// Semantic accents
val KineticAccentGreen = Color(0xFF4ADE80)              // green: #4ade80
val KineticAccentGreenGlow = Color(0x8C4ADE80)          // greenGlow: 55% (rgba(74, 222, 128, 0.55))
val KineticAccentGreenTint = Color(0x1A4ADE80)          // greenTint: 10% (rgba(74, 222, 128, 0.10))
val KineticAccentGreenTintSm = Color(0x1C4ADE80)        // greenTintSm: 11% (rgba(74, 222, 128, 0.11))
val KineticAccentBlue = Color(0xFF93C5FD)               // blue: #93c5fd
val KineticAccentBlueTint = Color(0x1A93C5FD)           // blueTint: 10% (rgba(147, 197, 253, 0.10))

// Interactive surfaces
val KineticSurfacePrimary = Color(0xFFFFFFFF)
val KineticSurfacePrimaryHover = Color(0xDEFFFFFF)      // primaryHover: 87% (rgba(255, 255, 255, 0.87))
val KineticSurfaceGhost = Color(0x0DFFFFFF)             // ghost: 5% (rgba(255, 255, 255, 0.05))
val KineticSurfaceGhostHover = Color(0x17FFFFFF)        // ghostHover: 9% (rgba(255, 255, 255, 0.09))
val KineticSurfaceTabActive = Color(0x14FFFFFF)         // tabActive: 8% (rgba(255, 255, 255, 0.08))
val KineticSurfaceSectionAlt = Color(0x03FFFFFF)        // sectionAlt: 1.2% (rgba(255, 255, 255, 0.012))

// Glass systems (Bento Panel background colors)
val KineticGlassBase = Color(0x0AFFFFFF)                // base background: 3.8% (rgba(255, 255, 255, 0.038))
val KineticGlassMd = Color(0x0FFFFFFF)                  // md background: 6% (rgba(255, 255, 255, 0.06))
val KineticGlassStrong = Color(0x17FFFFFF)              // strong background: 9% (rgba(255, 255, 255, 0.09))
