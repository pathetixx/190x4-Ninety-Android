package pw.x4.ninety.core.parser

/** Shared desktop-compatible links consumed by parser and config golden tests. */
object ProxyFixtures {
    data class Fixture(
        val id: String,
        val protocol: String,
        val name: String,
        val host: String,
        val port: Int,
        val type: String,
        val security: String,
        val link: String,
    )

    val all: List<Fixture> = listOf(
        Fixture(
            id = "vless",
            protocol = "vless",
            name = "VLESS Desktop",
            host = "2001:db8::1",
            port = 443,
            type = "xhttp",
            security = "reality",
            link = "vless://11111111-2222-3333-4444-555555555555@[2001:db8::1]:443?security=reality&type=xhttp&flow=xtls-rprx-vision&sni=edge.example.com&fp=chrome&pbk=public-key&sid=abcd&path=%2Fapi&host=cdn.example.com&mode=auto&extra=%7B%22xPaddingBytes%22%3A%22100-200%22%7D#VLESS%20Desktop",
        ),
        Fixture(
            id = "vmess",
            protocol = "vmess",
            name = "VMess Desktop",
            host = "vm.example.com",
            port = 443,
            type = "ws",
            security = "tls",
            link = "vmess://eyJ2IjoiMiIsInBzIjoiVk1lc3MgRGVza3RvcCIsImFkZCI6InZtLmV4YW1wbGUuY29tIiwicG9ydCI6IjQ0MyIsImlkIjoiMTExMTExMTEtMjIyMi0zMzMzLTQ0NDQtNTU1NTU1NTU1NTU1IiwiYWlkIjoiMCIsInNjeSI6ImF1dG8iLCJuZXQiOiJ3cyIsInR5cGUiOiJub25lIiwiaG9zdCI6ImNkbi5leGFtcGxlLmNvbSIsInBhdGgiOiIvc29ja2V0IiwidGxzIjoidGxzIiwic25pIjoiZWRnZS5leGFtcGxlLmNvbSIsImZwIjoiY2hyb21lIiwiYWxwbiI6ImgyLGh0dHAvMS4xIn0",
        ),
        Fixture(
            id = "trojan",
            protocol = "trojan",
            name = "Trojan Desktop",
            host = "trojan.example.com",
            port = 443,
            type = "grpc",
            security = "tls",
            link = "trojan://p%40ss%3Aword@trojan.example.com:443?security=tls&type=grpc&sni=edge.example.com&serviceName=trojan-grpc#Trojan%20Desktop",
        ),
        Fixture(
            id = "shadowsocks",
            protocol = "shadowsocks",
            name = "Shadowsocks Desktop",
            host = "ss.example.com",
            port = 8388,
            type = "tcp",
            security = "none",
            link = "ss://YWVzLTI1Ni1nY206cEBzczp3MHJk@ss.example.com:8388?plugin=v2ray-plugin%3Bmode%3Dwebsocket#Shadowsocks%20Desktop",
        ),
        Fixture(
            id = "hysteria2",
            protocol = "hysteria2",
            name = "Hysteria2 Desktop",
            host = "hy.example.com",
            port = 8443,
            type = "tcp",
            security = "none",
            link = "hysteria2://hy%40pass@hy.example.com:8443?sni=edge.example.com&obfs=salamander&obfs-password=secret&alpn=h3&insecure=1&up=50&down=200#Hysteria2%20Desktop",
        ),
        Fixture(
            id = "tuic",
            protocol = "tuic",
            name = "TUIC Desktop",
            host = "tuic.example.com",
            port = 443,
            type = "tcp",
            security = "none",
            link = "tuic://11111111-2222-3333-4444-555555555555:tuic%3Apass@tuic.example.com:443?sni=edge.example.com&alpn=h3&congestion_control=bbr&udp_relay_mode=native&zero_rtt_handshake=true#TUIC%20Desktop",
        ),
    )
}
