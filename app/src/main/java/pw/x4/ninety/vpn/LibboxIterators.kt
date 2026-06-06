package pw.x4.ninety.vpn

import io.nekohasekai.libbox.NetworkInterface
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.StringIterator

/** StringIterator поверх Kotlin-списка (для systemCertificates / addresses / dnsServer). */
class FixedStringIterator(private val list: List<String>) : StringIterator {
    private var i = 0
    override fun len(): Int = list.size
    override fun hasNext(): Boolean = i < list.size
    override fun next(): String = list[i++]
}

/** NetworkInterfaceIterator поверх Kotlin-списка (для getInterfaces). */
class FixedNetworkInterfaceIterator(
    private val list: List<NetworkInterface>,
) : NetworkInterfaceIterator {
    private var i = 0
    override fun hasNext(): Boolean = i < list.size
    override fun next(): NetworkInterface = list[i++]
}
