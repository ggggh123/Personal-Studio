package com.example.personal_studio.ui.theme.daylight

/**
 * Holds the light/dark family that is on screen and lets it change only at safe moments.
 *
 * Within a family the palette drifts every minute; that is imperceptible. Switching the
 * whole page between light and dark is not, so it must never happen while someone is reading
 * or typing. The theme proposes the sun's family every tick and calls [safePoint] when the
 * app comes back to the foreground, the screen turns on, or the user navigates.
 */
class FamilyGate(initial: ThemeFamily? = null) {
    var applied: ThemeFamily? = initial
        private set

    private var pending: ThemeFamily? = null

    /** Offers the family the sun implies now; returns the family to show. */
    fun propose(natural: ThemeFamily): ThemeFamily {
        val current = applied
        if (current == null) {
            applied = natural
            pending = null
            return natural
        }
        pending = if (natural != current) natural else null
        return current
    }

    /** A safe moment: apply a waiting change. Returns true when the family changed. */
    fun safePoint(): Boolean {
        val next = pending ?: return false
        pending = null
        if (next == applied) return false
        applied = next
        return true
    }

    /** Forget the held family, e.g. after the theme mode changes. */
    fun reset() {
        applied = null
        pending = null
    }

    val hasPending: Boolean get() = pending != null
}
