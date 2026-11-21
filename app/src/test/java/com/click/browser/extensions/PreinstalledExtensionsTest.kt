package com.click.browser.extensions

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the pre-installed extension bundle: every registry entry must match
 * the files actually shipped in the APK assets — same Gecko ID and pinned
 * version in the bundled manifest, no capability Click's add-on policy calls
 * unsupported, no unreviewable site-access pattern, a real PNG icon, and the
 * action popup the panel will offer to open. If any of these drift (e.g. a
 * future bundle refresh swaps a package), the build fails here instead of
 * shipping a fake or broken "already installed" entry.
 */
class PreinstalledExtensionsTest {

    @Test
    fun registryHasTheThreeEssentialExtensions() {
        assertEquals(
            listOf(
                "uBlock0@raymondhill.net",
                "addon@darkreader.org",
                "jid1-MnnxcxisBPnSXQ@jetpack"
            ),
            PreinstalledExtensions.entries.map { it.id }
        )
        assertEquals(
            PreinstalledExtensions.entries.size,
            PreinstalledExtensions.entries.map { it.folderName }.distinct().size
        )
        PreinstalledExtensions.entries.forEach { entry ->
            assertTrue(PreinstalledExtensions.isPreinstalled(entry.id))
            assertEquals(entry, PreinstalledExtensions.entry(entry.id))
            assertTrue(
                entry.location.startsWith("resource://android/assets/preinstalled_extensions/")
            )
            assertTrue(entry.location.endsWith("/"))
            assertTrue(ExtensionInstallPolicy.isOfficialAmoListing(entry.amoListingUrl))
            assertTrue(ExtensionInstallPolicy.isOfficialAmoXpiUrl(entry.xpiDownloadUrl))
            assertTrue(entry.xpiSha256.matches(Regex("[0-9a-f]{64}")))
        }
    }

    @Test
    fun bundledManifestsMatchTheRegistry() {
        PreinstalledExtensions.entries.forEach { entry ->
            val folder = assetDir("preinstalled_extensions/${entry.folderName}")
            val manifest = JSONObject(File(folder, "manifest.json").readText())

            val gecko = manifest.getJSONObject("browser_specific_settings").getJSONObject("gecko")
            assertEquals(entry.id, gecko.getString("id"))
            assertEquals(entry.version, manifest.getString("version"))

            val permissions = mutableListOf<String>()
            val permissionArray = manifest.optJSONArray("permissions")
            for (index in 0 until (permissionArray?.length() ?: 0)) {
                permissionArray?.optString(index)?.takeIf { it.isNotBlank() }?.let(permissions::add)
            }
            assertEquals(
                "Bundled ${entry.displayName} requests a capability Click cannot support",
                emptyList<String>(),
                ExtensionInstallPolicy.unsupportedPermissionNames(permissions)
            )
            val origins = permissions.filter { it == "<all_urls>" || it.contains("://") }
            assertEquals(
                "Bundled ${entry.displayName} requests an unreviewable site-access pattern",
                emptyList<String>(),
                ExtensionInstallPolicy.blockedOriginPatterns(origins)
            )
        }
    }

    @Test
    fun bundledIconsAndPopupsExist() {
        PreinstalledExtensions.entries.forEach { entry ->
            val icon = assetFile(entry.iconAssetPath)
            val iconBytes = icon.readBytes()
            assertTrue(
                "Icon for ${entry.displayName} must be a real PNG",
                iconBytes.size > 8 &&
                    iconBytes[0] == 0x89.toByte() && iconBytes[1] == 'P'.code.toByte() &&
                    iconBytes[2] == 'N'.code.toByte() && iconBytes[3] == 'G'.code.toByte()
            )

            val folder = assetDir("preinstalled_extensions/${entry.folderName}")
            val manifest = JSONObject(File(folder, "manifest.json").readText())
            val action = manifest.optJSONObject("browser_action") ?: manifest.optJSONObject("action")
            val popup = action?.optString("default_popup")?.takeIf { it.isNotBlank() }
            assertTrue("${entry.displayName} must declare an action popup", popup != null)
            assertTrue(
                "Popup file for ${entry.displayName} must be bundled",
                File(folder, popup!!.removePrefix("/")).isFile
            )
        }
    }

    private fun assetDir(relativePath: String): File = listOf(
        "src/main/assets/$relativePath",
        "app/src/main/assets/$relativePath"
    ).map(::File).firstOrNull { it.isDirectory }
        ?: error("Bundled asset directory $relativePath not found from the test working directory")

    private fun assetFile(relativePath: String): File = listOf(
        "src/main/assets/$relativePath",
        "app/src/main/assets/$relativePath"
    ).map(::File).firstOrNull { it.isFile }
        ?: error("Bundled asset $relativePath not found from the test working directory")
}
