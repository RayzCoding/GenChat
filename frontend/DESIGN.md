---
name: Synthetic Intelligence System
colors:
  surface: '#12131b'
  surface-dim: '#12131b'
  surface-bright: '#383842'
  surface-container-lowest: '#0d0e16'
  surface-container-low: '#1a1b23'
  surface-container: '#1e1f28'
  surface-container-high: '#292932'
  surface-container-highest: '#33343d'
  on-surface: '#e3e1ed'
  on-surface-variant: '#c2c6d7'
  inverse-surface: '#e3e1ed'
  inverse-on-surface: '#2f3039'
  outline: '#8c90a0'
  outline-variant: '#424654'
  surface-tint: '#b2c5ff'
  primary: '#b2c5ff'
  on-primary: '#002b73'
  primary-container: '#5c8cff'
  on-primary-container: '#002565'
  inverse-primary: '#0055d3'
  secondary: '#d8b9ff'
  on-secondary: '#450086'
  secondary-container: '#6904c5'
  on-secondary-container: '#d0acff'
  tertiary: '#00dfc1'
  on-tertiary: '#00382f'
  tertiary-container: '#00a38d'
  on-tertiary-container: '#003028'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#dae2ff'
  primary-fixed-dim: '#b2c5ff'
  on-primary-fixed: '#001848'
  on-primary-fixed-variant: '#0040a2'
  secondary-fixed: '#eedcff'
  secondary-fixed-dim: '#d8b9ff'
  on-secondary-fixed: '#290055'
  on-secondary-fixed-variant: '#6300bb'
  tertiary-fixed: '#26fedc'
  tertiary-fixed-dim: '#00dfc1'
  on-tertiary-fixed: '#00201a'
  on-tertiary-fixed-variant: '#005144'
  background: '#12131b'
  on-background: '#e3e1ed'
  surface-variant: '#33343d'
typography:
  display-lg:
    fontFamily: Geist
    fontSize: 48px
    fontWeight: '700'
    lineHeight: '1.1'
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Geist
    fontSize: 32px
    fontWeight: '600'
    lineHeight: '1.2'
  headline-md:
    fontFamily: Geist
    fontSize: 24px
    fontWeight: '600'
    lineHeight: '1.3'
  body-lg:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '400'
    lineHeight: '1.6'
  body-md:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: '1.6'
  label-md:
    fontFamily: Geist
    fontSize: 14px
    fontWeight: '500'
    lineHeight: '1.4'
    letterSpacing: 0.05em
  mono-code:
    fontFamily: JetBrains Mono
    fontSize: 14px
    fontWeight: '400'
    lineHeight: '1.5'
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  base: 4px
  container-padding: 24px
  element-gap: 16px
  section-margin: 48px
  sidebar-width: 280px
---

## Brand & Style

This design system is engineered for high-performance AI interfaces, prioritizing a sense of "intelligence behind the glass." The brand personality is **visionary, technical, and precise**, evoking the feeling of a sophisticated command center rather than a simple consumer app.

The aesthetic follows a **Cyber-Glassmorphism** style. It utilizes deep, dark backgrounds to ground the experience, layered with translucent, frosted-glass surfaces that suggest depth and light refraction. Key interfaces use vibrant gradients and "glow" borders to represent data energy and active processing. The target audience includes data scientists, power users, and tech-forward professionals who value speed and technical clarity.

## Colors

The palette is optimized for OLED displays and low-light environments. 
- **Surface Foundations:** The base layer is a deep `#0B0C14`. Secondary surfaces use subtle shifts in luminosity to define hierarchy without losing the "infinite dark" feel.
- **Accents:** Electric Blue and Cyber Purple are reserved for primary actions and active states. Mint Green is used strictly for success states and generative completion indicators.
- **Translucency:** Backgrounds for modals and sidebars should use 60-80% opacity with a 20px-40px backdrop blur to maintain the glass effect.

## Typography

This design system uses a combination of **Geist** for structural headings and **Inter** for long-form content. 
- **Headlines:** Use Geist for a technical, sharp edge. Large displays should utilize negative letter-spacing to feel more compact and impactful.
- **Body:** Inter provides high legibility for AI-generated responses and documentation. 
- **Code & Technical Data:** Use a monospaced font like JetBrains Mono for code blocks, terminal outputs, and metadata strings to reinforce the "high-tech" narrative.

## Layout & Spacing

The layout follows a **Fixed-Fluid Hybrid** model.
- **Navigation:** A fixed left-hand sidebar (280px) houses primary navigation and history.
- **Content:** The main workspace is fluid, centered with a maximum readable width of 1024px for chat interfaces.
- **Rhythm:** An 8px grid system governs all spacing. Use generous 24px margins for containers to allow the "glass" edges to breathe.
- **Mobile:** On devices < 768px, the sidebar collapses into a bottom drawer or hamburger menu, and horizontal padding reduces to 16px.

## Elevation & Depth

Hierarchy is established through **Backdrop Blur** and **Inner Glows** rather than traditional drop shadows.
- **Level 1 (Base):** Deep `#0B0C14`.
- **Level 2 (Panels):** Surface at 70% opacity with a 1px solid border at 10% white opacity.
- **Level 3 (Active/Popups):** Surface with a 2px "Electric Blue" to "Cyber Purple" gradient border (low opacity) and a 30px backdrop blur.
- **Glows:** Primary buttons and active indicators feature a soft outer glow (`box-shadow: 0 0 15px rgba(62, 123, 255, 0.3)`) to simulate light emission from the UI.

## Shapes

The design system utilizes **large, generous radii** to offset the technical "sharpness" of the typography. 
- **Standard UI Elements:** (Buttons, Inputs) 8px radius.
- **Containers:** (Cards, Chat Bubbles, Modals) 16px to 24px radius.
- **Specialty:** User avatars and status pips remain fully circular.

## Components

- **Buttons:** Primary buttons use the `action_primary` gradient with white text. Secondary buttons are ghost-styled with 1px semi-transparent borders.
- **Chat Bubbles:** AI responses should be styled as glass panels with a subtle blue inner glow. User inputs should have a solid Cyber Purple background to differentiate clearly.
- **Input Fields:** Search and message bars feature a dark background (slightly lighter than the base) with a focus state that activates a 1px Mint Green border.
- **Chips/Badges:** Small, pill-shaped elements with 10% opacity fills of the accent colors and high-contrast labels.
- **Icons:** Use 1.5px stroke weight. For active states, apply a "neon" filter (drop-shadow) in the corresponding accent color.
- **Progress Indicators:** Use the Mint Green for completion. For active "thinking" states, use a shimmering gradient animation across the glass surface.