package pw.x4.ninety.data.persistence

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64
import pw.x4.ninety.core.model.WarpRegistration
import pw.x4.ninety.core.model.WarpRegistrationSanitizer

class WarpStore(private val file: File, private val codec: SecretCodec) {
    fun read(): WarpRegistration? = runCatching {
        if (!file.exists()) return null
        WarpWire.decode(codec.decrypt(file.readText()))
            ?.let { WarpRegistrationSanitizer.sanitize(it).registration }
    }.getOrNull()

    fun write(value: WarpRegistration) {
        val clean = requireNotNull(WarpRegistrationSanitizer.sanitize(value).registration)
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".new")
        FileOutputStream(temp).use {
            it.write(codec.encrypt(WarpWire.encode(clean)).toByteArray())
            it.fd.sync()
        }
        runCatching {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }.recoverCatching {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }.getOrThrow()
    }

    fun clear() {
        file.delete()
        File(file.parentFile, file.name + ".new").delete()
    }

    companion object {
        fun create(context: Context) = WarpStore(
            File(context.applicationContext.filesDir, "warp-registration.enc"),
            AndroidKeystoreSecretCodec(),
        )
    }
}

internal object WarpWire {
    private const val VERSION = "warp-v1"

    fun encode(v: WarpRegistration): String = listOf(
        VERSION, enc(v.registrationId), enc(v.accountId), enc(v.accessToken), enc(v.privateKey),
        enc(v.peerPublicKey), enc(v.localIpv4), enc(v.localIpv6), enc(v.clientId),
        enc(v.license.orEmpty()), if (v.warpPlus) "1" else "0", enc(v.accountType), enc(v.registeredAt),
    ).joinToString("\n")

    fun decode(raw: String): WarpRegistration? = runCatching {
        val p = raw.lines()
        if (p.size != 13 || p[0] != VERSION) return null
        WarpRegistration(
            registrationId = dec(p[1]), accountId = dec(p[2]), accessToken = dec(p[3]),
            privateKey = dec(p[4]), peerPublicKey = dec(p[5]), localIpv4 = dec(p[6]),
            localIpv6 = dec(p[7]), clientId = dec(p[8]), license = dec(p[9]).ifBlank { null },
            warpPlus = p[10] == "1", accountType = dec(p[11]), registeredAt = dec(p[12]),
        )
    }.getOrNull()

    private fun enc(v: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(v.toByteArray())
    private fun dec(v: String) = Base64.getUrlDecoder().decode(v).toString(Charsets.UTF_8)
}
