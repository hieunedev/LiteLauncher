# NovaOS — Design System v1.0

## Visual direction
Premium Dark Liquid Glass: deep near-black canvas, translucent layered panels, restrained cool highlights, subtle edge strokes and soft depth. The reference direction is elegant and legible, not a blur-heavy imitation.

## Tokens
- Background: #080B12
- Surface: #111827
- Elevated surface: #182235
- Primary text: #F4F7FC
- Secondary text: #A6B1C4
- Accent: #83B9FF
- Border: white at 10–14% opacity
- Radius: 16 dp cards, 24 dp large panels, 12 dp controls
- Spacing: 4 dp base scale (4, 8, 12, 16, 24, 32)
- Motion: 140–220 ms for small transitions; avoid continuous decorative motion.

## Glass rules
1. Blur is progressive enhancement, never required for readability.
2. Provide opaque fallback on low-end GPUs and when reduced effects is enabled.
3. Maintain text contrast over wallpaper; use a scrim when needed.
4. Use translucency selectively; do not stack many full-screen blurs.
5. Shadows and highlights should communicate hierarchy, not decoration.

## Components
- GlassCard: translucent/elevated container with fallback surface.
- IconTile: app icon with stable hit target and label.
- DockItem: icon-first action with accessibility label.
- SectionHeader: compact title and optional action.
- QuickAction: icon, title and explicit state.
- ClockBlock: scalable time/date hierarchy.

## Screen direction
- Lock companion: large editorial clock, compact date, notification preview area and minimal shortcuts.
- Home: quiet wallpaper canvas, restrained widgets, clear dock and predictable grid.
- Drawer: high-density searchable app list with efficient scrolling.
- Control: grouped quick actions in a bottom sheet / dedicated panel.

## Accessibility
Text scale must remain usable. Never encode state using color alone. Interactive targets should generally be at least 48 dp.
