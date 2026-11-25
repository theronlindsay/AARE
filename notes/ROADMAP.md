# Working notes (dev branch only — not for upstream PRs)

PR branches are cut from upstream `main` per feature; this file stays on
`claude/aare-improvements-2fkbg2`.

## Phone
- GrapheneOS (Android 12+), not rooted → parked apps need Shizuku via wireless
  debugging; restart after every reboot, needs Wi-Fi to start.

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
   - Car: Kia CL4, Mobis head unit. Declared panel: 800x480.
   - DriveDeck log of the car: offers 1920x1080 (margin 0x360 → 1920x720 visible),
     1280x720 (0x240 → 1280x480), 800x480 (0x77). Real screen is ultra-wide; the
     800x480 panel is a legacy placeholder whose shape matches none of them.
   - Fits the latch hypothesis: x latches after a tap past x=800, y after y>480.
   - Workaround now: in-app touch calibration (replaces auto mapping).
   - Fix plan: (a) persist latched source size per head unit; (b) maybe: a declared
     panel whose aspect matches no offered visible area → start from visible area.
     Need AARE's own log (`input:` / `projection` lines) to confirm stream vs native coords.
   - AARE diag log (2026-11-23/24, wireless, standard_m_6, AAP 4.1): `input: display 0 panel
     800x480`. Default choice is likely 1920x1080 (visible 1920x720) → pre-latch taps are
     2.4x/1.5x off. Verbose log still needed for the `projection` line.
   - Heuristic candidate: declared panel == codec size of an offered config AND its shape
     matches none of the visible areas → placeholder; use the car's visible pixels.
   - Build blocker: dl.google.com (Android SDK) blocked by the session network policy.

   - STATUS: user confirmed a right-edge tap fixes it. Fix committed on the dev branch
     (placeholder-panel heuristic + diag lines `touch: mapping from …`); tests pass. Not yet
     tested in the car; not yet a PR. Per-car persistence deferred.

## Other problems seen in the user's log (not yet triaged)
- First attempts with built-in/self-signed certs get refused before the extracted p12 is
  tried; Mobis units may refuse retries (see README Kia Cerato row).
- `Binding socket to network N failed: EPERM` on wireless join (12:39, twice).
- Shizuku helper died mid-session ("the Shizuku helper went away"; "the helper didn't start").
- Repeated "no frame from the encoder for ~1.3 s" while navigating.
- Video focus flips native→projected right after connecting.
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

## Workflow
- One branch per fix, cut from upstream main (e.g. `fix/touch-placeholder-panel`), pushed to
  the fork; PRs to prototux/AARE only when the user asks. The dev branch carries the notes,
  the test-build workflow and every fix, for test releases.
- Test releases: push a tag like `1.6.0-test.1` on the dev branch, only when the user says.
- Branches: fix/touch-placeholder-panel (touch fix).

## Constraint
- Everything should be upstreamable to prototux/AARE as PRs; no long-lived fork.

## DriveDeck findings (read 2026-11-25; AGPL-3.0 — reimplement ideas only, copy no code)
- Touch: treats head-unit taps as already in projected-video coordinates; ignores the
  declared panel size (a unit declaring 1258x708 with 800x480 video). Supports our fix.
- Parked display: owned by a Shizuku user service. Preferred path is VirtualDeviceManager
  (shell-owned companion association, VirtualDisplayConfig flags PUBLIC|OWN_CONTENT_ONLY|
  DESTROY_CONTENT_ON_REMOVAL|TRUSTED, setIgnoreActivitySizeRestrictions on API 36+, a
  VirtualTouchscreen + VirtualDpad tied to the display). Fallback: DisplayManager with the
  same flags + IME policy local.
- Launch: `am start --display` / startActivityAsUser as com.android.shell; no windowing mode;
  no global settings changes.
- Guest density: raised until the short side is <=360dp (strip screens) or <=540dp, so apps use
  their phone layout. AARE currently gives guests the car's density → likely cause of apps
  not looking native.
- Multi-touch up to 10 pointers.
- Voice: wake word, sherpa-onnx VAD, on-device Gemma (LiteRT-LM) for STT + intents, Piper/Kokoro
  TTS; optional cloud via LiveKit. GrapheneOS has no system speech recognizer.

## User answers (2026-11-25)
- Parked apps: all stuck loading in AARE (more testing to come).
- Ungoogled Maps (nl-morphe-patches, gitlab.com/johninnld/nl-morphe-patches): its
  "Native navigation for any Android Auto host" patch adds a Car App Library navigation
  service and names AARE as a supported host; in AARE it shows an error that official
  Android Auto is needed. AARE's ProjectedAppCatalog only knows com.google.android.apps.maps
  (renamed package not found). Package: org.ungoogled.android.apps.maps. Still need the exact
  message and where it shows. The message is not in the patch's code, so it is Maps' own.
- Parked layout: full screen only when the app has a fullscreen option enabled; per-app
  setting for how much of the screen it takes (e.g. maps left, music player right).
- Voice: cloud API, open models, cheap and predictable pricing (no pay-as-you-go surprises).
  Wants a general phone assistant too, not only in the car.
- Voice hosting: user has a Raspberry Pi 4 (home server) and a light VPS → Lapin server viable.
- Defaults: navigation/music app chosen in settings and remembered.
