# Cloudflare WARP integration

## Scope

Android Ninety registers a device with Cloudflare, creates a local WireGuard key pair and adds a typed sing-box WireGuard endpoint named `warp`.

Implemented modes:

- `Direct`: Android TUN → WARP → Internet.
- `Chain`: Android TUN → selected Ninety proxy → WARP → Internet.

Desktop endpoint scanning, deep scan and automatic periodic re-scan are intentionally deferred. Android accepts a manually supplied endpoint and defaults to `engage.cloudflareclient.com:2408`.

## Registration transaction

```text
generate local WireGuard key pair
             ↓
POST Cloudflare registration with public key
             ↓
optional WARP+ license activation
             ↓
validate returned id/token/peer/address/client_id
             ↓
AES/GCM encrypted atomic local commit
             ↓
best-effort delete previous remote registration
```

If activation, validation or local commit fails, the provisional remote registration is deleted best effort and the previous local registration remains active.

Reset performs remote deletion best effort, always removes the local encrypted payload and disables WARP in Options.

## Secret boundary

The following values never enter `optionsJson`, Room, logs or Compose status:

- WireGuard private key;
- Cloudflare access token;
- WARP+ license.

They are stored together in `filesDir/warp-registration.enc` through the existing `AndroidKeystoreSecretCodec`:

- AES/GCM;
- Android Keystore key material;
- temporary file + fsync;
- atomic replace when supported;
- invalid or corrupted payloads load as no registration.

Non-secret settings remain in Options/DataStore:

- enabled;
- direct/chain mode;
- endpoint;
- MTU;
- noise preset and custom ranges.

The WARP+ license field uses password transformation and is cleared from Compose state after successful registration.

## Config mapping

When registration is valid and WARP is enabled, `NinetyConfigBuilder` adds:

```json
{
  "endpoints": [
    {
      "type": "wireguard",
      "tag": "warp",
      "address": ["<v4>/32", "<v6>/128"],
      "private_key": "<local key>",
      "mtu": 1280,
      "peers": [
        {
          "address": "engage.cloudflareclient.com",
          "port": 2408,
          "public_key": "<Cloudflare peer key>",
          "allowed_ips": ["0.0.0.0/0", "::/0"],
          "reserved": [0, 0, 0]
        }
      ]
    }
  ]
}
```

Direct mode has no endpoint detour. Chain mode adds `detour: "proxy"`.

WARP also becomes:

- `route.final`;
- detour for `dns-remote`;
- detour for remote rule-set downloads;
- target of custom routing action `Proxy`.

Direct/LAN/region/block rules keep their explicit actions and therefore remain above the final WARP route.

If registration, endpoint or key material is invalid, WARP decoration is omitted and the ordinary proxy config remains intact.

## Noise presets

- `Off`: no fake packets.
- `Default`: count `1-3`, size `10-30`, delay `10-30`.
- `Aggressive`: count `3-8`, size `30-90`, delay `5-15`.
- `Custom`: normalized user ranges.

The config is deterministic for identical settings and registration data.

## Quality Engine interaction

- Chain mode continues to use Quality Engine because the selected proxy is the first hop.
- Direct mode does not record quality batches or trigger automatic proxy reloads because proxy selection is not on the active final path.
- Manual node selection remains untouched in both modes.

## UI

WARP is placed below custom rules in `Настройки → Маршрутизация`:

- free registration;
- optional WARP+ activation;
- registered plan/state;
- enable/disable;
- Direct/Chain;
- endpoint and MTU;
- noise presets/custom ranges;
- apply and reset.

Changing the enabled state or applying settings uses the existing serialized generation-safe VPN reload.

## Automated tests

Core tests cover:

- registration normalization and validation;
- exact WARP+ license length;
- endpoint and range normalization;
- Direct and Chain JSON;
- IPv6 endpoint parsing;
- reserved client bytes;
- DNS/final/custom-route mapping;
- noise presets;
- invalid-registration fallback;
- byte determinism.

Data tests cover:

- encrypted round trip;
- absence of plaintext private key;
- atomic temporary-file cleanup;
- corrupted/invalid payload recovery;
- local reset.

CI only validates code and generated configuration. It does not contact Cloudflare or establish a real WARP tunnel.

## Mandatory device smoke test

Before ready-for-review:

1. Register a free WARP account on a clean install.
2. Confirm the private key/license/token do not appear in Options, diagnostics or logs.
3. Restart the app and confirm registration survives.
4. Connect in Direct mode and verify public egress/DNS use WARP.
5. Connect in Chain mode and verify proxy remains the first hop.
6. Switch Auto/manual nodes in Chain mode and verify serialized reload.
7. Confirm Direct mode does not flap/reload when node quality changes.
8. Test IPv4 endpoint, bracketed IPv6 endpoint and invalid endpoint fallback.
9. Test MTU 1280 and at least one alternative value.
10. Test Off/Default/Aggressive/Custom noise against the pinned libbox build.
11. Activate a valid WARP+ license and verify plan state.
12. Test invalid/expired license: previous registration must remain usable.
13. Re-register and verify old remote registration cleanup does not break the new one.
14. Reset while disconnected and while connected.
15. Force network loss during registration/activation/local commit and verify rollback.
16. Test app/device restart, VPN revoke and Wi-Fi/mobile transitions.
17. Test Compact/Medium/Expanded UI and increased font scale.

Until this matrix passes, PR #1 remains draft.
