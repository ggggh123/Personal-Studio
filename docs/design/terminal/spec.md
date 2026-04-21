# 夜机 · Terminal — Design Language Spec

**Status:** ACTIVE · 2026-04-21
**Applies to:** all screens from P0 onward
**Archived alternatives:** [`../archive/README.md`](../archive/README.md) — 10 alternative directions explored & shelved

---

## Essence

Personal-Studio is **a CRT terminal reborn as a study companion**. Not a chat app with a dark theme. Not a notebook with green text. It looks, sounds, and behaves like a power-tool — a REPL for thought, a log for a day, a grep-able knowledge base. The user is not "chatting with an AI assistant"; they are **driving a tool** that happens to understand natural language.

> **Vocabulary check:** we say *session*, *log*, *entry*, *archive*, *query*, not *chat*, *bubble*, *card*, *feed*.

### One-sentence differentiators

- Every surface is mono-spaced.
- Every interaction has a keyboard metaphor.
- Every result is a log line.
- Every accent is a single saturated hue.

---

## Color tokens

All colors named from the CRT-phosphor tradition. Dark is primary and canonical; a secondary "amber phosphor" night-mode variant is optional future work.

### Base surfaces

| Token | Hex | Role |
|---|---|---|
| `void` | `#0A0E0C` | primary background — near-black with 0.1 green cast |
| `deep` | `#121815` | elevated surface (cards, modals) |
| `hull` | `#1A221D` | pressed / hover state |
| `rule` | `#2A4A32` | hairline borders, frames |
| `dim` | `#3D5A42` | inactive text, disabled elements |

### Foreground text

| Token | Hex | Role |
|---|---|---|
| `foam` | `#D8E5DA` | primary body text (not pure white — slightly green-cast) |
| `foam-mute` | `#8FA896` | secondary text, metadata |
| `foam-dim` | `#5A7260` | tertiary text, helper copy |

### Accents (use sparingly, never decoratively)

| Token | Hex | Role |
|---|---|---|
| `phosphor` | `#41FF8F` | primary accent — prompts, active state, user focus |
| `amber` | `#FFB548` | secondary accent — warnings, math/formula, timers |
| `cyan` | `#6AC3E8` | tertiary accent — section labels, code identifiers, AI output |
| `carmine` | `#FF5770` | destructive / overdue / error only |
| `olive` | `#B6D477` | success / completed / done |

### Semantic mapping (Material3)

```kotlin
val TerminalDarkScheme = darkColorScheme(
    primary            = Phosphor,      // #41FF8F
    onPrimary          = Void,          // #0A0E0C
    secondary          = Amber,         // #FFB548
    onSecondary        = Void,
    tertiary           = Cyan,          // #6AC3E8
    onTertiary         = Void,
    background         = Void,
    onBackground       = Foam,          // #D8E5DA
    surface            = Void,
    onSurface          = Foam,
    surfaceVariant     = Deep,          // #121815
    onSurfaceVariant   = FoamMute,      // #8FA896
    outline            = Rule,          // #2A4A32
    outlineVariant     = Dim,           // #3D5A42
    error              = Carmine,       // #FF5770
    onError            = Void,
)
```

### Forbidden colors

Gradients, glassy translucencies, purple / magenta accents, pastel hues, soft whites (#FFFFFF), pure blacks (#000000). The green-cast in `void` and `foam` is a signature; do not flatten it.

---

## Typography

Monospace discipline is the soul of this direction. **Almost everything is mono.** The single exception is math/formulas, which are italic serif for legibility.

### Font stack

| Role | Font | Fallback | Size/Weight |
|---|---|---|---|
| **Display / H1** | `VT323` (decorative CRT font) | `JetBrains Mono 700` | 28–40sp regular |
| **UI / body** | `JetBrains Mono 400/500` | `Noto Sans Mono` | 14–16sp |
| **Body fine print** | `JetBrains Mono 400` | `Noto Sans Mono` | 11–12sp |
| **CJK body** | `Noto Sans SC 400` (not mono, gracefully) | `Noto Serif SC` | 14sp, letter-spacing 0.02em |
| **Math / formula** | `Fraunces` italic `opsz 96` | serif | 14–18sp |
| **Code identifiers** | `JetBrains Mono 500` in `cyan` | `JetBrains Mono` | same as body |

### Rationale

- **JetBrains Mono** is the canonical free mono for CS audiences — warm, ligature-rich, excellent at small sizes.
- **VT323** is a free CRT-bitmap pastiche — used only for display titles (e.g., hero headings like `/extrema`). Never for body.
- **CJK pragma:** a true mono CJK font (like Source Han Mono) is ~40 MB. Too heavy for an Android APK. We use `Noto Sans SC` at monospace-adjacent sizes with slight tracking. The effect is "technical but readable Chinese", not mono-width.
- **Fraunces italic for formulas** is an intentional break in the mono discipline — it signals "this is math, not code." Math should feel hand-set.

### Type scale (Compose)

```kotlin
val TerminalTypography = Typography(
    displayLarge   = t("VT323", weight = 400, size = 40.sp, line = 44.sp, letterSpacing = 0.sp),
    displayMedium  = t("VT323", weight = 400, size = 32.sp, line = 36.sp),
    displaySmall   = t("JetBrains Mono", weight = 700, size = 22.sp, line = 28.sp, letterSpacing = (-0.01).em),
    headlineMedium = t("JetBrains Mono", weight = 600, size = 18.sp, line = 24.sp),
    titleLarge     = t("JetBrains Mono", weight = 600, size = 16.sp, line = 22.sp),
    titleMedium    = t("JetBrains Mono", weight = 500, size = 14.sp, line = 20.sp),
    titleSmall     = t("JetBrains Mono", weight = 500, size = 12.sp, line = 16.sp, letterSpacing = 0.1.em),
    bodyLarge      = t("JetBrains Mono", weight = 400, size = 14.sp, line = 22.sp),
    bodyMedium     = t("JetBrains Mono", weight = 400, size = 13.sp, line = 20.sp),
    bodySmall      = t("JetBrains Mono", weight = 400, size = 11.sp, line = 16.sp),
    labelLarge     = t("JetBrains Mono", weight = 500, size = 12.sp, line = 16.sp, letterSpacing = 0.15.em),
    labelSmall     = t("JetBrains Mono", weight = 500, size = 10.sp, line = 14.sp, letterSpacing = 0.2.em),
)
```

All labels (`labelLarge`, `labelSmall`) are **ALL CAPS** with tracking — they mimic command-line flag names.

---

## Shape & radius

Terminal has no rounded corners. Every surface is a rectangle.

```kotlin
val TerminalShapes = Shapes(
    extraSmall  = RoundedCornerShape(0.dp),   // chips — still rectangles
    small       = RoundedCornerShape(0.dp),
    medium      = RoundedCornerShape(0.dp),
    large       = RoundedCornerShape(0.dp),
    extraLarge  = RoundedCornerShape(0.dp),
)
```

Only exception: the **blinking cursor block** itself (not a shape primitive — just a colored Box).

---

## Dividers, borders, frames

- **Hairlines**: 1dp solid `rule` (`#2A4A32`)
- **Dashed dividers**: 1dp dashed — use `drawBehind { drawLine(pathEffect = PathEffect.dashPathEffect(...)) }` or an equivalent Compose modifier. Dashed is the default separator style for chat/log boundaries.
- **Prompt rules**: for the top app bar bottom edge, use 1dp dashed `phosphor`.

No drop shadows. No blur. No elevation.

---

## Texture / overlay

### CRT scan lines

Every screen has a faint repeating scan-line pattern over the background.

Compose implementation (single shared Modifier, applied at the root of `AppTheme`'s content or per-screen):

```kotlin
Modifier.drawWithCache {
    val stripeColor = Phosphor.copy(alpha = 0.025f)
    onDrawWithContent {
        drawContent()
        var y = 0f
        while (y < size.height) {
            drawLine(stripeColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            y += 3.dp.toPx()
        }
    }
}
```

### Vignette

Subtle radial-gradient darker at screen edges (40% transparent black at corners, 0% at center). Single layer over the root.

### No animation on the overlays

The scan lines do *not* move. Static by design. This prevents "cheesy retro" feel.

---

## Motion

Minimal and purposeful. Three canonical motions:

| Motion | When | Duration | Curve |
|---|---|---|---|
| **Cursor blink** | always, on any input + streaming output | 1s steps(2) | `steps(2)` |
| **Type-on** (AI streaming) | when Gemini streams text | per-chunk, ease-out | ~40ms per word |
| **Fade-in** (new log line) | when a new message/task appears | 180ms | ease-out |

Forbidden: spring animations, bounces, slide-ins, scale transforms. All motion is either blink, fade, or character-typing.

---

## Voice / microcopy

All UI copy leans **command-line terse**. Imperatives, lowercase, no exclamation marks.

| UI element | English | 中文 |
|---|---|---|
| Send message | `send` / `↵` | `发送` |
| Save to KB | `archive` / `[a]` | `归档` |
| Copy | `copy` / `[c]` | `复制` |
| Regenerate | `regen` / `[r]` | `重做` |
| Overdue | `late` | `逾期` |
| Completed | `done` | `完成` |
| Empty state (Chat) | `no sessions yet · press "new" to start` | `无会话 · 按"新建"开始` |
| Empty state (KB) | `no entries yet · archive a chat to populate` | `无条目 · 从对话中归档` |
| Loading | `...` (blinking phosphor dots) | same |
| Error | `error: <message>` on a carmine line | `error: <消息>` |

**Forbidden copy**: "Hello!", "Welcome!", any welcome mat or onboarding friendliness. The app assumes competence.

---

## Screen-level conventions

### Top app bar (all screens)

Prompt-style: `studio:~/<route> $`. The `$` itself is `phosphor`. To the right: a single character glyph for navigation (`⚙` settings, `←` back, `⋮` menu). Bottom border is 1dp dashed `phosphor`.

Example:
```
┌──────────────────────────────────────┐
│ studio:~/chat $               ⚙      │
│ ╌ ╌ ╌ ╌ ╌ ╌ ╌ ╌ ╌ ╌ ╌ ╌ ╌ ╌ ╌ ╌ ╌ ╌ │
└──────────────────────────────────────┘
```

### Chat session

Messages are **log lines**, not bubbles.

- User messages: `user@study:~$ <message>` — `user@study` in `amber`, `:~$` in `dim`, message in `foam`.
- AI messages: framed in a box with a top-left label `── gemini ──────`.
  - Streaming text renders character-by-character with a blinking cursor at the tail.
  - When `Done` arrives, cursor disappears, and a line below shows: `── done · <duration> · <tokens> tokens ──` in `foam-dim`.
- Math inside AI messages: `> f'(x) = 2ax + b` where `>` is `dim` and the expression is `amber` in `Fraunces italic`.
- Action chips below AI responses: `[a]rchive · [c]opy · [r]egen` in `foam-dim` with the letter in brackets `phosphor`. Tap any of them; the key-hint UI suggests "you could also type `a` if this were a real terminal" (it's not, but the affordance sells the world).

### Input bar (Chat)

`> <placeholder>` + blinking cursor. No send button — just `press ↵` hint on the right in `foam-dim` (the keyboard's return key). Attachment entry is a `[+]` hotkey to its right.

### Empty states

Large monospace ASCII art or a plain prompt line. No illustrations, no mascots.

```
▓ studio:~/knowledge $ ls
no entries yet
▓ archive a chat session to populate this log.
```

### Timeline

A daily log. Each item renders as:

```
08:00  ━━  COURSE   高等数学                                [Mon]
10:00  ━━  COURSE   线性代数 · done                         [✓]
13:42  ──  now
14:00  ━━  DUE      Android 作业 1 · 4h left                [4h]
16:30  ━━  SELF     跑步                                    [self]
19:00  ━━  LATE     英语听写 · overdue                      [late!]
```

- Time in `JetBrains Mono 14sp`.
- `━━` for present/future, `──` for the "now" row.
- Category label in `titleSmall` all-caps, color-coded: `COURSE`=cyan, `DUE`=amber, `LATE`=carmine, `SELF`=foam, `DONE`=olive strikethrough.
- Content in `bodyMedium`.
- Tag on the right in `labelSmall` with a 1dp `rule` border. Overdue has filled `carmine` background.

### Knowledge entry

Renders like a `man` page.

```
# 二次函数求极值                                §3.2
────────────────────────────────────────────
CATEGORY    数学 / 高数 / 极值
SOURCE      chat/2026-04-21-14:32 · scan/p.48

## 核心概念
...

## 推导
> f'(x) = 2ax + b
令 f'(x) = 0 得 x = -b/2a。

## 关联
→ 导数与单调性
→ 闭区间最值问题
```

- `#` title is `displaySmall` (`JetBrains Mono 700`).
- `##` section heads are `titleMedium` in `phosphor`.
- `→` for related links in `cyan`.

### Settings

Every setting is a `key = value` row.

```
API_KEY              ●●●●●●●●●● (set)     [edit] [clear]
MODEL                gemini-1.5-flash     [change]
THEME                terminal · phosphor  [change]
TIMETABLE            13 periods           [edit]
NOTIFICATIONS        allowed              [↗ system]
```

Left column `phosphor`, equals sign `dim`, current value `foam`, actions `cyan` (in brackets to mimic shortcut).

---

## Accessibility

- Primary text contrast `foam` on `void` ≈ 10:1 WCAG AAA.
- Accent `phosphor` on `void` ≈ 12:1 AAA. `amber` on `void` ≈ 9:1 AAA.
- `carmine` on `void` ≈ 5.5:1 AA (acceptable for error-only use; consider underlining when it must carry meaning).
- `cyan` on `void` ≈ 8:1 AAA.
- Never use color alone to encode state — always pair with a label (`done`, `late`, etc.).
- Vignette must not obscure text — ensure it caps at 40% opacity in the darkest corner pixel.

---

## Dark-mode-only (P0)

The app ships dark-only during P1–P5. A "light phosphor" variant (amber-on-ivory, not sage-on-cream) may be explored in P6 if budget permits.

System dark-mode toggle: we ignore it. The app is always dark. This is an intentional product decision.

---

## Fonts to bundle

Add to `app/src/main/res/font/`:

| File | Source | License |
|---|---|---|
| `jetbrains_mono_variable.ttf` | https://github.com/JetBrains/JetBrainsMono | OFL 1.1 |
| `vt323_regular.ttf` | https://github.com/phoikoi/VT323 (Google Fonts mirror) | OFL 1.1 |
| `fraunces_italic_variable.ttf` | https://github.com/undercasetype/Fraunces | OFL 1.1 |
| `noto_sans_sc_variable.ttf` (weights 300-700 subset) | Google Noto CJK | OFL 1.1 |

Subset the Noto Sans SC to only the weights used (300, 400, 500, 700) and to **GB2312 + common punctuation** character set to keep APK size reasonable (~3–5 MB). If GB2312 is insufficient for rare characters, fall back to system CJK at runtime.

APK size budget for fonts: **≤ 10 MB total**.

---

## Implementation checklist (for Compose theme update)

1. Create `ui/theme/Color.kt` — define all tokens above.
2. Create `ui/theme/Typography.kt` — wire Material3 `Typography` to `FontFamily` resources loaded from `res/font/`.
3. Create `ui/theme/Shape.kt` — zero-radius.
4. Create `ui/theme/TerminalScheme.kt` — the `darkColorScheme` call.
5. Update `ui/theme/Theme.kt` — `AppTheme` uses `TerminalDarkScheme` + `TerminalTypography` + `TerminalShapes`, always dark (ignore `isSystemInDarkTheme`).
6. Create `ui/theme/ScanLineOverlay.kt` — reusable `Modifier.scanLines()`.
7. Create `ui/components/TerminalTopBar.kt` — `studio:~/<route> $` prompt-style.
8. Create `ui/components/PromptLine.kt` — for user message rows.
9. Create `ui/components/AiFrame.kt` — for AI response boxes.
10. Refactor existing `MainScreen.kt`, `SettingsScreen.kt`, placeholder screens to use the new components.
11. Download and embed fonts in `res/font/`.
12. Rebuild, smoke-test on device, check contrast ratios.

Estimated effort: **2–3 focused days** of AI-dispatched implementation.

---

## Open questions (deferred)

- CJK monospace: is Noto Sans SC + tracking good enough, or should we invest in a subset of Source Han Mono?
- Do we need a "presentation mode" (fullscreen, extra-large text) for studying at a desk with the phone propped up?
- Keyboard shortcut hints in chip UI: do we actually wire real hardware keyboard bindings for users on tablet/dex? Defer to P6.
