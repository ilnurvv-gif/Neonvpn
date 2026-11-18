package com.neonvpn.app

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.util.Base64
import java.util.zip.Inflater

/**
 * Turns whatever the user gives us into a WireGuard / AmneziaWG config text:
 *  - a plain .conf text ([Interface] ... [Peer] ...)
 *  - an Amnezia key "vpn://..." (base64url of qCompress(JSON))
 *  - an Amnezia QR code, including multi-part QR codes
 */
object ConfigImporter {

    sealed class Result {
        data class Ok(val name: String, val conf: String) : Result()
        data class NeedMoreQr(val received: Int, val total: Int) : Result()
        data class Fail(val message: String) : Result()
    }

    private const val QR_MAGIC = 1984
    private val qrChunks = mutableMapOf<Int, ByteArray>()
    private var qrTotal = 0

    fun resetQr() {
        qrChunks.clear()
        qrTotal = 0
    }

    fun parse(raw: String, fallbackName: String): Result {
        val text = raw.trim().removePrefix("﻿")
        if (text.isEmpty()) return Result.Fail("Пусто: нечего импортировать")

        if (text.contains("[Interface]", ignoreCase = true)) {
            return Result.Ok(fallbackName, text)
        }

        val key = text.removePrefix("vpn://").filterNot { it.isWhitespace() }
        val bytes = decodeBase64(key)
            ?: return Result.Fail("Не удалось распознать ключ или конфиг")

        if (bytes.size > 8 && readShort(bytes) == QR_MAGIC) {
            return handleQrChunk(bytes, fallbackName)
        }
        return parseAmneziaBytes(bytes, fallbackName)
    }

    private fun handleQrChunk(bytes: ByteArray, fallbackName: String): Result {
        return try {
            val input = DataInputStream(ByteArrayInputStream(bytes))
            input.readShort()
            val total = input.readUnsignedByte()
            val id = input.readUnsignedByte()
            val len = input.readInt()
            val data = ByteArray(len)
            input.readFully(data)
            if (total != qrTotal) {
                qrChunks.clear()
                qrTotal = total
            }
            qrChunks[id] = data
            if (qrChunks.size < qrTotal) {
                Result.NeedMoreQr(qrChunks.size, qrTotal)
            } else {
                val out = ByteArrayOutputStream()
                for (i in 0 until qrTotal) out.write(qrChunks[i] ?: ByteArray(0))
                resetQr()
                parseAmneziaBytes(out.toByteArray(), fallbackName)
            }
        } catch (e: Exception) {
            resetQr()
            Result.Fail("Не удалось прочитать QR-код")
        }
    }

    private fun parseAmneziaBytes(bytes: ByteArray, fallbackName: String): Result {
        val jsonBytes = qUncompress(bytes) ?: bytes
        val root = try {
            JSONObject(String(jsonBytes, Charsets.UTF_8))
        } catch (e: Exception) {
            return Result.Fail("Ключ не похож на ключ Amnezia")
        }
        return fromAmneziaJson(root, fallbackName)
    }

    private fun fromAmneziaJson(root: JSONObject, fallbackName: String): Result {
        val containers = root.optJSONArray("containers")
        if (containers == null || containers.length() == 0) {
            return Result.Fail(
                "В ключе нет готового конфига. Ключи подписки Amnezia Premium/Free не поддерживаются, " +
                    "нужен ключ от своего сервера или файл .conf"
            )
        }

        val defaultName = root.optString("defaultContainer")
        val ordered = (0 until containers.length())
            .mapNotNull { containers.optJSONObject(it) }
            .sortedByDescending { it.optString("container") == defaultName }

        val otherProtocols = mutableSetOf<String>()
        val dns1 = root.optString("dns1").ifBlank { "1.1.1.1" }
        val dns2 = root.optString("dns2").ifBlank { "1.0.0.1" }

        for (container in ordered) {
            for (protocol in container.keys()) {
                if (protocol == "container") continue
                val proto = container.optJSONObject(protocol) ?: continue
                val lastConfigText = proto.optString("last_config")
                if (lastConfigText.isBlank()) continue
                val lastConfig = try { JSONObject(lastConfigText) } catch (e: Exception) { continue }

                var conf = lastConfig.optString("config")
                if (!conf.contains("[Interface]")) {
                    if (protocol != "awg" && protocol != "wireguard") {
                        otherProtocols += protocol
                        continue
                    }
                    conf = buildFromFields(lastConfig, root) ?: continue
                }
                conf = conf.replace("\$PRIMARY_DNS", dns1).replace("\$SECONDARY_DNS", dns2)

                val name = root.optString("description")
                    .ifBlank { root.optString("hostName") }
                    .ifBlank { fallbackName }
                return Result.Ok(name, conf)
            }
        }

        return if (otherProtocols.isNotEmpty()) {
            Result.Fail(
                "В ключе только протокол ${otherProtocols.joinToString()}. " +
                    "Neon VPN поддерживает AmneziaWG и WireGuard"
            )
        } else {
            Result.Fail("В ключе нет конфигурации AmneziaWG или WireGuard")
        }
    }

    private fun buildFromFields(lc: JSONObject, root: JSONObject): String? {
        val privateKey = lc.optString("client_priv_key")
        val publicKey = lc.optString("server_pub_key")
        val clientIp = lc.optString("client_ip")
        val host = lc.optString("hostName").ifBlank { root.optString("hostName") }
        val port = lc.optString("port")
        if (privateKey.isBlank() || publicKey.isBlank() || clientIp.isBlank() || host.isBlank() || port.isBlank()) {
            return null
        }
        val sb = StringBuilder()
        sb.append("[Interface]\n")
        sb.append("PrivateKey = ").append(privateKey).append('\n')
        sb.append("Address = ").append(if (clientIp.contains('/')) clientIp else "$clientIp/32").append('\n')
        sb.append("DNS = \$PRIMARY_DNS, \$SECONDARY_DNS\n")
        lc.optString("mtu").takeIf { it.isNotBlank() }?.let { sb.append("MTU = ").append(it).append('\n') }
        for (k in listOf("Jc", "Jmin", "Jmax", "S1", "S2", "S3", "S4", "H1", "H2", "H3", "H4", "I1", "I2", "I3", "I4", "I5")) {
            val v = lc.optString(k)
            if (v.isNotBlank()) sb.append(k).append(" = ").append(v).append('\n')
        }
        sb.append("\n[Peer]\n")
        sb.append("PublicKey = ").append(publicKey).append('\n')
        lc.optString("psk_key").takeIf { it.isNotBlank() }?.let { sb.append("PresharedKey = ").append(it).append('\n') }
        sb.append("AllowedIPs = 0.0.0.0/0, ::/0\n")
        sb.append("Endpoint = ").append(host).append(':').append(port).append('\n')
        sb.append("PersistentKeepalive = 25\n")
        return sb.toString()
    }

    private fun decodeBase64(s: String): ByteArray? {
        if (s.isEmpty()) return null
        return try {
            Base64.getUrlDecoder().decode(s)
        } catch (e: IllegalArgumentException) {
            try { Base64.getDecoder().decode(s) } catch (e2: IllegalArgumentException) { null }
        }
    }

    private fun readShort(b: ByteArray): Int =
        ((b[0].toInt() and 0xFF) shl 8) or (b[1].toInt() and 0xFF)

    /** Qt's qUncompress: 4-byte big-endian length followed by a zlib stream. */
    private fun qUncompress(b: ByteArray): ByteArray? {
        if (b.size < 5) return null
        val expected = ((b[0].toInt() and 0xFF) shl 24) or ((b[1].toInt() and 0xFF) shl 16) or
            ((b[2].toInt() and 0xFF) shl 8) or (b[3].toInt() and 0xFF)
        if (expected <= 0 || expected > 50_000_000) return null
        val inflater = Inflater()
        return try {
            inflater.setInput(b, 4, b.size - 4)
            val out = ByteArray(expected)
            var n = 0
            while (n < expected && !inflater.finished()) {
                val read = inflater.inflate(out, n, expected - n)
                if (read == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                n += read
            }
            if (n == 0) null else out.copyOf(n)
        } catch (e: Exception) {
            null
        } finally {
            inflater.end()
        }
    }
}
