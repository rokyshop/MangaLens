package fr.mangalens.network

/**
 * Small, auditable host denylist. Exact suffix matching (not substring matching)
 * avoids blocking e.g. notdoubleclick.net. Not a full EasyList engine.
 */
object AdBlocker {
    private val blockedDomains = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google.com", "adnxs.com", "adsrvr.org", "criteo.com",
        "criteo.net", "taboola.com", "outbrain.com", "mgid.com",
        "popads.net", "popcash.net", "propellerads.com", "exoclick.com",
        "exosrv.com", "adsterra.com", "juicyads.com", "trafficjunky.net",
        "onclickads.net", "hilltopads.net", "clickadu.com", "monetag.com",
        "pubmatic.com", "rubiconproject.com", "openx.net", "smartadserver.com",
        "adskeeper.co.uk", "revcontent.com", "bidvertiser.com"
    )

    fun shouldBlock(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val normal = host.trimEnd('.').lowercase(java.util.Locale.ROOT)
        return blockedDomains.any { normal == it || normal.endsWith(".$it") }
    }
}
