# Ninety Android — desktop parity rewrite

Desktop repository `pathetixx/190x4-Ninety` is the visual source of truth.

## Contract

- Preserve Android VPN, persistence, WARP, routing and OTA behavior.
- Match desktop hierarchy, materials, typography and motion instead of producing an Android-inspired approximation.
- Keep the desktop 400×400 Hero HUD geometry: 72/90 ticks, segmented rings, target brackets, integrity gauge, clock, curved status/target labels and diagnostics.
- Keep the desktop 292dp expanded sidebar and adaptive compact/medium navigation.
- Support all 16 desktop themes, including Kintsugi Noir, Aurora Glass, Porcelain Zero and Titanium Signal with their own material layers.
- Compact layouts may reflow, but must preserve the same hierarchy and visual identity.

## Stages

1. Token/material registry and root shell.
2. HeroMask/HUD and Home composition.
3. Profiles and Nodes desktop cards/grid/list states.
4. Settings master-detail controls and theme previews.
5. WARP, dialogs, logs/OTA surfaces and final responsive/device matrix.

The PR remains draft until CI, screenshots and physical-device interaction checks pass.
