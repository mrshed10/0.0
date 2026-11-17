package com.tvbrowser.aosp.extensions

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Converts Chromium extensions (Chrome / Brave / Kiwi / Edge .crx, or unpacked .zip)
 * into a Gecko-installable .xpi:
 *  1. strips the CRX2/CRX3 header (a CRX is a ZIP with a signed header in front);
 *  2. patches manifest.json for Gecko:
 *     - adds browser_specific_settings.gecko.id (required for MV3 / unsigned installs),
 *     - MV3 "background.service_worker" -> "background.scripts" (Gecko uses event pages),
 *     - removes Chrome-only keys (update_url, key, ...);
 *  3. drops Chrome's _metadata/ (verified_contents) folder.
 *
 * Files already signed by Mozilla (META-INF/mozilla.rsa or cose.sig) are left untouched.
 */
object CrxConverter {

    data class Result(val file: File, val signedByMozilla: Boolean, val converted: Boolean)

    fun prepare(input: File, outDir: File, fallbackId: String): Result {
        val raw = input.readBytes()
        val zipStart = findZipStart(raw)
        outDir.mkdirs()
        val zip = File(outDir, "$fallbackId.zip")
        FileOutputStream(zip).use { it.write(raw, zipStart, raw.size - zipStart) }

        val out = File(outDir, "$fallbackId.xpi")
        ZipFile(zip).use { zf ->
            val signed = zf.getEntry("META-INF/mozilla.rsa") != null || zf.getEntry("META-INF/cose.sig") != null
            if (signed) {
                zip.copyTo(out, overwrite = true)
                zip.delete()
                return Result(out, signedByMozilla = true, converted = zipStart > 0)
            }
            ZipOutputStream(FileOutputStream(out)).use { zos ->
                val entries = zf.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    if (e.isDirectory || e.name.startsWith("_metadata/")) continue
                    zos.putNextEntry(ZipEntry(e.name))
                    if (e.name == "manifest.json") {
                        val text = zf.getInputStream(e).bufferedReader().use { it.readText() }
                        zos.write(patchManifest(text, fallbackId).toByteArray())
                    } else {
                        zf.getInputStream(e).use { it.copyTo(zos) }
                    }
                    zos.closeEntry()
                }
            }
        }
        zip.delete()
        return Result(out, signedByMozilla = false, converted = true)
    }

    fun patchManifest(text: String, fallbackId: String): String {
        val m = JSONObject(text.removePrefix("﻿"))

        val bss = m.optJSONObject("browser_specific_settings")
            ?: m.optJSONObject("applications")
            ?: JSONObject()
        val gecko = bss.optJSONObject("gecko") ?: JSONObject()
        if (!gecko.has("id")) gecko.put("id", "$fallbackId@crx.tvbrowser")
        bss.put("gecko", gecko)
        m.remove("applications")
        m.put("browser_specific_settings", bss)

        m.optJSONObject("background")?.let { bg ->
            if (bg.has("service_worker") && !bg.has("scripts")) {
                bg.put("scripts", JSONArray().put(bg.getString("service_worker")))
                bg.remove("service_worker")
            }
        }
        for (k in listOf("update_url", "key", "minimum_chrome_version", "differential_fingerprint", "externally_connectable")) {
            m.remove(k)
        }
        return m.toString(2)
    }

    private fun le32(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xff) or
            ((b[off + 1].toInt() and 0xff) shl 8) or
            ((b[off + 2].toInt() and 0xff) shl 16) or
            ((b[off + 3].toInt() and 0xff) shl 24)

    private fun isPk(b: ByteArray, i: Int) =
        i + 3 < b.size && b[i] == 'P'.code.toByte() && b[i + 1] == 'K'.code.toByte() &&
            b[i + 2] == 3.toByte() && b[i + 3] == 4.toByte()

    private fun findZipStart(b: ByteArray): Int {
        if (b.size > 16 && b[0] == 'C'.code.toByte() && b[1] == 'r'.code.toByte() &&
            b[2] == '2'.code.toByte() && b[3] == '4'.code.toByte()
        ) {
            val start = when (le32(b, 4)) {
                2 -> 16 + le32(b, 8) + le32(b, 12)  // magic, version, pubkey len, sig len
                3 -> 12 + le32(b, 8)                // magic, version, header len
                else -> -1
            }
            if (start > 0 && isPk(b, start)) return start
        }
        if (isPk(b, 0)) return 0
        for (i in 0 until b.size - 4) if (isPk(b, i)) return i
        throw IOException("Not a CRX / ZIP / XPI file")
    }
}
