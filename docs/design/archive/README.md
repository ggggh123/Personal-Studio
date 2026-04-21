# Design Direction Archive

**Date created:** 2026-04-21
**Status:** Reference / archive — **not the active design language**
**Active direction:** 夜机 · Terminal (Option I) — see [`../terminal/spec.md`](../terminal/spec.md)

---

## Context

During the design-language exploration phase before starting P1 (Chat MVP), eleven distinct aesthetic directions were prototyped. The goal was to escape generic "AI for students" visual defaults (dark-mode + purple gradients + Inter) and commit to a distinctive point of view.

After reviewing all eleven, the project committed to **Option I · 夜机 Terminal** as the single design language for the app. The remaining ten are archived here in case any of the following happens:

- P1–P6 complete ahead of schedule and there's budget to ship an alternate theme
- The chosen direction doesn't test well in real daily use and a pivot is needed
- Elements from one archived direction are borrowed for a sub-surface (e.g., a special "study journal" export view)

The HTML prototypes in this folder are self-contained (they reference Google Fonts via CDN and use inline CSS; open them in any modern browser).

---

## The Eleven Directions

Previews show the same chat exchange rendered in each aesthetic: `user: 这道题怎么解？` → AI answer with the derivative `f′(x) = 2ax + b`.

### Round 1 — the initial thesis

| # | Name | One-line essence | Preview |
|---|---|---|---|
| **A** | **印刻 · Folio** | scholarly publishing applied to a live app — warm paper, deep ink, vermilion seal | [`01-folio-deep-dive.html`](01-folio-deep-dive.html) |

A got a full deep-dive treatment with its own standalone page before B and C were sketched.

### Round 2 — A / B / C side by side

| # | Name | Essence | Preview |
|---|---|---|---|
| A | Folio (revisited) | a scholar's notebook, at 2 a.m. | [`02-a-b-c-comparison.html`](02-a-b-c-comparison.html) |
| **B** | **Graphite Atlas** | a technical handbook that happens to chat — carbon + signal orange | ↑ same file |
| **C** | **Midnight Codex** | reading lamp at 1 a.m. — deep teal + persimmon, dark only | ↑ same file |

### Round 3 — D / E / F / G broader genre sweep

| # | Name | Essence | Preview |
|---|---|---|---|
| **D** | **风滋 · Risograph Zine** | indie 2-color zine print — riso pink + teal over cream, chunky display type | [`03-d-e-f-g-variety.html`](03-d-e-f-g-variety.html) |
| **E** | **静谧 · Whisper** | Muji-like restraint — lowercase, generous whitespace, one sage accent | ↑ same file |
| **F** | **风物志 · Monument** | Swiss poster meets editorial — heroic Anton display type, red ember | ↑ same file |
| **G** | **草木 · Herbarium** | botanical study journal — pressed-flower palette, Young Serif | ↑ same file |

### Round 4 — H / I / J / K cross-cultural extremes

| # | Name | Essence | Preview |
|---|---|---|---|
| **H** | **墨染 · Ink Wash** | Chinese sumi-e — rice paper, ink wash, 朱砂 seal stamps as UI | [`04-h-i-j-k-cross-cultural.html`](04-h-i-j-k-cross-cultural.html) |
| **I** | **夜机 · Terminal** ✓ | CRT phosphor + monospace + REPL aesthetic — **chosen** | ↑ same file |
| **J** | **包豪斯 · Bauhaus** | 1920s modernism — primary yellow/blue/red, Jost, geometric shapes | ↑ same file |
| **K** | **历书 · Almanac** | vintage schoolhouse textbook — oat cream, saturated navy/red, Bitter slab-serif | ↑ same file |

---

## Why I was chosen

**夜机 · Terminal** was picked for three reasons:

1. **Zero overlap with existing "AI for students" apps.** Every competitor uses rounded bubble chat + soft gradients. A monospace REPL with CRT scan lines cannot be confused with any of them.
2. **Genuine fit for the user.** The app's primary user codes and writes technical content. A terminal-native aesthetic frames the whole app as a tool for thought rather than a chat product.
3. **Technical coherence.** Monospace typography is a radical discipline that simplifies a lot of UI decisions (alignment, measure, vertical rhythm) rather than complicating them.

Trade-offs accepted: cold to humanities-oriented users, strongly gendered "techy" connotation, harder to animate gracefully than bubble UIs.

---

## How to use this archive

If you later decide to try one of the archived directions:

1. Open its HTML preview in a browser to re-familiarize with the aesthetic.
2. Write a corresponding `docs/design/<name>/spec.md` following the structure of [`../terminal/spec.md`](../terminal/spec.md).
3. Create a new Compose theme variant under `app/src/main/java/.../ui/theme/themes/` (e.g., `FolioTheme.kt`) that wraps `MaterialTheme` with swapped `colorScheme` + `Typography` + `Shape` tokens.
4. Add a selector in `SettingsScreen` so the user can toggle themes at runtime. Persist selection in DataStore (`UserPreferencesRepository`).
5. Be aware that **shallow skinning** (color + font + radius swaps) may not capture the distinctive DNA of directions like Ink Wash (brush strokes), Bauhaus (geometric decoration), or Monument (heroic type). Those require per-theme Composable overrides for core components (`ChatBubble`, `AppBar`, etc.) to feel correct. Budget accordingly.

---

## Files in this directory

- `README.md` — this index
- `01-folio-deep-dive.html` — Option A's initial deep-dive exploration
- `02-a-b-c-comparison.html` — A/B/C side-by-side
- `03-d-e-f-g-variety.html` — D/E/F/G genre sweep
- `04-h-i-j-k-cross-cultural.html` — H/I/J/K cross-cultural round
