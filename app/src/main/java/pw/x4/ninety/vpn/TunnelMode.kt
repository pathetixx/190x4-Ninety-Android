package pw.x4.ninety.vpn

import android.content.Context
import pw.x4.ninety.data.Options
import pw.x4.ninety.data.Store

enum class TunnelMode(
    val wireName: String,
    val title: String,
    val description: String,
) {
    PROXY(
        wireName = "proxy",
        title = "Прокси",
        description = "Обычная Ninety-нода или штатный Auto",
    ),
    WARP_DIRECT(
        wireName = "warp-direct",
        title = "WARP",
        description = "Прямой туннель Cloudflare без proxy-ноды",
    ),
    WARP_CHAIN(
        wireName = "warp-chain",
        title = "Цепочка",
        description = "Ninety-нода → Cloudflare WARP",
    );

    val requiresProxySelection: Boolean
        get() = this != WARP_DIRECT

    val usesWarp: Boolean
        get() = this != PROXY
}

data class TunnelStartCheck(
    val allowed: Boolean,
    val message: String? = null,
)

object TunnelModes {
    fun current(options: Options.Data = Options.data): TunnelMode = when {
        !options.warpEnabled -> TunnelMode.PROXY
        options.warpMode == "chain" -> TunnelMode.WARP_CHAIN
        else -> TunnelMode.WARP_DIRECT
    }

    fun select(context: Context, mode: TunnelMode) {
        Options.update(context) { current ->
            when (mode) {
                TunnelMode.PROXY -> current.copy(warpEnabled = false)
                TunnelMode.WARP_DIRECT -> current.copy(warpEnabled = true, warpMode = "direct")
                TunnelMode.WARP_CHAIN -> current.copy(warpEnabled = true, warpMode = "chain")
            }
        }
    }

    fun checkStart(mode: TunnelMode = current()): TunnelStartCheck = when (mode) {
        TunnelMode.PROXY -> if (Store.hasRunnableSelection()) {
            TunnelStartCheck(true)
        } else {
            TunnelStartCheck(false, "Добавьте профиль и выберите ноду или режим «Авто»")
        }

        TunnelMode.WARP_DIRECT -> if (WarpRuntime.snapshot.registered) {
            TunnelStartCheck(true)
        } else {
            TunnelStartCheck(false, "Сначала зарегистрируйте WARP в Настройки → Маршрутизация")
        }

        TunnelMode.WARP_CHAIN -> when {
            !WarpRuntime.snapshot.registered -> TunnelStartCheck(
                false,
                "Сначала зарегистрируйте WARP в Настройки → Маршрутизация",
            )
            !Store.hasRunnableSelection() -> TunnelStartCheck(
                false,
                "Для WARP Chain выберите Ninety-ноду или режим «Авто»",
            )
            else -> TunnelStartCheck(true)
        }
    }

    fun activeLabel(mode: TunnelMode = current()): String? = when (mode) {
        TunnelMode.PROXY -> Store.activeNodeLabel()
        TunnelMode.WARP_DIRECT -> "Cloudflare WARP"
        TunnelMode.WARP_CHAIN -> Store.activeNodeLabel()?.let { "$it → WARP" } ?: "WARP Chain"
    }
}
