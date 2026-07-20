package pw.x4.ninety.data.persistence

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val urlCiphertext: String,
    val used: Long,
    val total: Long,
    val expire: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "nodes",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profileId")],
)
data class NodeEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val protocol: String,
    val name: String,
    val host: String,
    val port: Int,
    val uuidCiphertext: String,
    val passwordCiphertext: String,
    val method: String,
    val cipher: String,
    val alterId: Int,
    val security: String,
    val transport: String,
    val flow: String,
    val sni: String,
    val fingerprint: String,
    val publicKey: String,
    val shortId: String,
    val alpn: String,
    val path: String,
    val hostHeader: String,
    val serviceName: String,
    val mode: String,
    val extra: String,
    val upMbps: Int,
    val downMbps: Int,
    val obfs: String,
    val obfsPasswordCiphertext: String,
    val certificatePublicKeySha256: String,
    val congestionControl: String,
    val udpRelayMode: String,
    val insecure: Boolean,
    val zeroRttHandshake: Boolean,
    val disableSni: Boolean,
    val plugin: String,
    val pluginOptions: String,
    val rawCiphertext: String,
    val fromSubscription: Boolean,
)
