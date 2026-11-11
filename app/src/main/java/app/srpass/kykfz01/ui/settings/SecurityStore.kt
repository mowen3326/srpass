package app.srpass.kykfz01.ui.settings

import android.content.Context

/**
 * Security preferences that must survive process death.
 *
 * Kept apart from [ThemeStore]: these are not cosmetic, and mixing them would
 * make a future "reset security settings" wipe the theme as well.
 */
class SecurityStore(context: Context) {

    private val prefs = context.getSharedPreferences("srpass_security", Context.MODE_PRIVATE)

    /**
     * Whether FLAG_SECURE is applied to the main window.
     *
     * Defaults to **on**: blocking capture is the safe state, and the flag is
     * applied before the first frame so content is never briefly exposed.
     */
    fun getBlockScreenshots(): Boolean = prefs.getBoolean(KEY_BLOCK_SCREENSHOTS, true)

    fun setBlockScreenshots(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BLOCK_SCREENSHOTS, enabled).apply()
    }

    /**
     * Whether the hidden spaces exist at all: one switch for all four.
     *
     * Defaults to **off**: nothing hidden is reachable until the user switches
     * the feature on in Settings -> Security. An app nobody has configured must
     * not already have a private drawer, even an empty one.
     *
     * Reads the old single-module key as a fallback so anyone who had already
     * turned this on keeps it on; the new key wins from here on.
     */
    fun getHiddenSpaceEnabled(): Boolean {
        if (prefs.contains(KEY_HIDDEN_SPACE)) return prefs.getBoolean(KEY_HIDDEN_SPACE, false)
        return prefs.getBoolean(KEY_PULL_TO_HIDDEN, false)
    }

    fun setHiddenSpaceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIDDEN_SPACE, enabled).apply()
    }

    private companion object {
        const val KEY_BLOCK_SCREENSHOTS = "block_screenshots"
        const val KEY_HIDDEN_SPACE = "hidden_space_enabled"
        /** Pre-four-modules key, kept only to migrate an existing choice. */
        const val KEY_PULL_TO_HIDDEN = "pull_to_hidden"
    }
}
