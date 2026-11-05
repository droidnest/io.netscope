package io.netscope

/**
 * No-op stub matching the real [NotificationStyle] enum in netscope-core.
 * Used by the release build so that consumer code and netscope-ui compile
 * against the same type regardless of which artifact is on the classpath.
 */
enum class NotificationStyle(val key: String, val label: String, val description: String) {
    NONE    ("none",     "Off",      "No notification shown"),
    MINIMAL ("minimal",  "Minimal",  "Live request counter only"),
    DETAILED("detailed", "Detailed", "Counter · errors · last code");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: DETAILED
    }
}
