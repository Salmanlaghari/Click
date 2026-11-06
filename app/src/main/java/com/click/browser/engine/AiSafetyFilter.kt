package com.click.browser.engine

/**
 * Pre-call content moderation for the AI chat (Google Play "AI-Generated
 * Content" policy compliance).
 *
 * [isBlocked] runs BEFORE any network request: if it returns true the prompt
 * is never sent to Groq/OpenRouter and the user sees a system message instead.
 * Matching is case-insensitive with alphanumeric lookarounds, so "classic"
 * does not trigger on "class", while multi-word terms like "nude pics" still
 * match. No network needed — the list is bundled.
 */
object AiSafetyFilter {

    private val BLOCKED_TERMS: Set<String> = setOf(
        // ---- Sexual / explicit content ----
        "porn", "pornography", "pornhub", "xxx", "hentai", "erotica", "erotic",
        "masturbat", "orgasm", "blowjob", "handjob", "cunnilingus", "fellatio",
        "deepthroat", "gangbang", "threesome", "orgy", "bdsm", "fetish",
        "sexting", "nude pics", "nudes", "onlyfans leak", "camgirl",
        "escort service", "prostitut", "brothel", "stripper", "strip club",
        "vibrator", "dildo", "sex toy", "bukake", "bukkake", "creampie",
        "milf", "incest", "bestiality", "zoophilia", "pedophil", "child porn",
        "lolicon", "shotacon", "upskirt", "downblouse", "revenge porn",
        "non-consensual sex", "nonconsensual sex", "rape fantasy", "sex slave",
        "sexual assault", "molest", "grooming a minor", "ageplay",
        // ---- Violence / gore ----
        "murder", "assassinate", "assassination", "how to kill", "kill him",
        "kill her", "kill them", "torture", "behead", "decapitat", "dismember",
        "gore", "snuff film", "mass shooting", "school shooting",
        "suicide bomb", "terrorist attack", "strangl", "throat slit",
        "cannibal", "necrophilia", "massacre", "lynch", "genocide",
        "ethnic cleansing", "shoot up", "stab to death", "beat to death",
        // ---- Hate slurs ----
        "nigger", "nigga", "faggot", "fag", "dyke", "tranny", "shemale",
        "chink", "gook", "kike", "wetback", "spic", "raghead", "towelhead",
        "sandnigger", "coon", "porch monkey", "beaner", "paki",
        "kill all", "death to",
        // ---- Self-harm ----
        "suicide", "kill myself", "end my life", "self-harm", "self harm",
        "cutting myself", "cut myself", "how to hang", "slit my wrists",
        "slit your wrists", "suicide note", "painless suicide",
        "suicide methods", "how to overdose", "overdose on pills",
        "jump off a bridge", "no reason to live",
        // ---- Weapons / explosives manufacturing ----
        "how to make a bomb", "pipe bomb", "molotov", "molotov cocktail",
        "gunpowder recipe", "how to make gunpowder", "tnt recipe",
        "c4 explosive", "how to make c4", "pressure cooker bomb",
        "napalm recipe", "how to make napalm", "thermite recipe",
        "ricin", "anthrax", "sarin", "mustard gas", "chlorine bomb",
        "build a ghost gun", "ghost gun", "3d printed gun",
        "convert to full auto", "full auto conversion", "silencer build",
        "how to make a silencer", "untraceable gun",
        // ---- Illicit drug manufacturing ----
        "meth recipe", "how to make meth", "how to cook meth",
        "fentanyl synthesis", "how to make fentanyl", "lsd synthesis",
        "how to make lsd", "cocaine extraction", "heroin synthesis",
        "mdma synthesis", "how to make mdma", "dmt extraction",
        "grow op", "drug lab"
    )

    /**
     * Single alternation regex over all terms (one pass, not 130+ scans).
     * Multi-word terms are split and re-joined with \s+ AFTER escaping, and
     * lookarounds (not \b) guard the edges so terms like "nude pics" or
     * "how to make a bomb" match reliably.
     */
    private val pattern: Regex by lazy {
        val alternation = BLOCKED_TERMS.joinToString("|") { term ->
            term.split(" ").joinToString("\\s+") { Regex.escape(it) }
        }
        Regex("(?i)(?<![a-z0-9])($alternation)(?![a-z0-9])")
    }

    /**
     * Returns true if the prompt contains blocked content and must NOT be
     * sent to the AI provider.
     */
    fun isBlocked(prompt: String): Boolean {
        if (prompt.isBlank()) return false
        return pattern.containsMatchIn(prompt)
    }
}
