// Package xraybridge — тонкая gomobile-обёртка над xray-core. Вшивается ВТОРЫМ
// пакетом в libbox.aar (один Go-рантайм, two-core): xhttp-ноды поднимаются в
// локальном xray (socks-inbound + xhttp-outbound), sing-box ходит к нему по SOCKS.
//
// Логика портирована из XTLS/libXray (xray/xray.go + controller) — saму libXray как
// зависимость подключить нельзя (module path github.com/xtls/libxray без /v26 при
// CalVer-тегах v26.x → go get отвергает). xray-core (major v1) импортируется штатно.
//
// CI копирует этот файл в hsb/xraybridge/ и биндит вместе с ./experimental/libbox.
package xraybridge

import (
	"runtime/debug"
	"syscall"

	"github.com/xtls/xray-core/core"
	_ "github.com/xtls/xray-core/main/distro/all"
	xinternet "github.com/xtls/xray-core/transport/internet"
)

var instance *core.Instance

// RunXrayFromJSON стартует xray из JSON-конфига. core.StartInstance создаёт И
// запускает инстанс (как RunXrayFromJSON в libXray — без отдельного Start).
func RunXrayFromJSON(configJSON string) error {
	srv, err := core.StartInstance("json", []byte(configJSON))
	if err != nil {
		return err
	}
	instance = srv
	debug.FreeOSMemory()
	return nil
}

// StopXray гасит инстанс (идемпотентно).
func StopXray() error {
	if instance != nil {
		err := instance.Close()
		instance = nil
		return err
	}
	return nil
}

// GetXrayState — запущен ли инстанс.
func GetXrayState() bool { return instance != nil && instance.IsRunning() }

// XrayVersion — версия ядра.
func XrayVersion() string { return core.Version() }

// Protector — мост к VpnService.protect(fd): исходящие сокеты xray не должны
// уходить в TUN sing-box (иначе петля). Реализуется на стороне Kotlin.
type Protector interface{ Protect(fd int) bool }

// RegisterDialerController вешает protect на каждый исходящий сокет xray.
// Звать ДО RunXrayFromJSON. Зависит от xray:api:beta (есть в сборке).
func RegisterDialerController(p Protector) {
	xinternet.RegisterDialerController(func(network, address string, conn syscall.RawConn) error {
		return conn.Control(func(fd uintptr) { p.Protect(int(fd)) })
	})
}
