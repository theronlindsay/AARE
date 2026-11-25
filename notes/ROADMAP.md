# Working notes (dev branch only — not for upstream PRs)

PR branches are cut from upstream `main` per feature; this file stays on
`claude/aare-improvements-2fkbg2`.

## Primary apps
- Ungoogled Maps (works in DriveDeck, not AARE), de-Waze, ViVi

## Issues
1. **Touch lands in the wrong spot for the first few seconds, then fine.**
   - Lead: `TouchMapper.observe()` (app/.../car/TouchMapper.kt). If the car declares a
     touch panel smaller than the coordinates it really sends, taps are scaled by the
     declared panel until one lands past its edge, then that axis latches to the
     visible area. Per-axis, per-session, never remembered.
   - Alt lead: a UI config update from the head unit re-lays out (`relayOut`) a few
     seconds in.
   - Need from user: Diagnostics log lines `input: display … panel WxH` and
     `projection WxH…` for their car; car make/model.
   - Candidate fix: remember the latched source size per head unit (KnownHeadUnits)
     so later sessions start right; or infer from the first touch.

## Features
1. **Parked apps that run natively on the car screen** (not phone mirror).
   - Exists: `launch/LaunchHostService.kt` (Shizuku/root, VirtualDisplay +
     setLaunchDisplayId + injectInputEvent), UI `ui/car/StandaloneAppScreen.kt`.
   - Gaps found:
     - Single pointer only (`touch(... pointerId)` always id 0) → no pinch-zoom in maps.
     - VirtualDisplay flags lack SUPPORTS_TOUCH (1<<6), ROTATES_WITH_CONTENT,
       DESTROY_CONTENT_ON_REMOVAL, OWN_FOCUS (scrcpy uses these).
     - No handling of non-resizeable activities (letterboxing / refusing secondary display).
   - Reference: DriveDeck (codeberg.org/JohninNL/drivedeck) — not yet read, codeberg
     is blocked by this session's network policy.
2. **LLM voice assistant** that can start navigation and control music.
   - Check Lapin (github.com/prototux/lapin), already car-aware, before building new.
3. **Ungoogled Maps support** (works in DriveDeck) — likely falls out of #1.

## Constraint
- Everything should be upstreamable to prototux/AARE as PRs; no long-lived fork.
