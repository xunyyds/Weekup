# Third-party notices

## shiguangschedule — desktop-mode request interception pattern

- Author / copyright: **Copyright (C) 2025 XingHeYuZhuan**.
- Upstream: https://github.com/XingHeYuZhuan/ShiguangSchedule (the source checkout used here is `shiguangschedule-main`).
- License: **Apache License, Version 2.0**.
- Source consulted: `shared/src/commonMain/kotlin/.../ui/schoolselection/web/WebBridgeProtocol.kt` (`JS_INTERCEPT_POST` design), `shared/src/androidMain/kotlin/.../ui/schoolselection/web/AndroidWebConstants.kt`, `WebViewRequestInterceptor.kt`, `WebCompatDelegate.kt`, and `WebView.android.kt`.
- Adapted code (all rewritten for this project, not translated):
  - `android/app/src/main/assets/web/xwake_post_bridge.js` — document-start hook that copies XHR/fetch/form POST payloads to native memory and marks the request.
  - `android/app/src/main/kotlin/com/xu/wake/ui/web/XwakeDesktopChannel.kt` — the `@JavascriptInterface` receiver, the payload stash, and the document-start installation.
  - `android/app/src/main/kotlin/com/xu/wake/ui/web/XwakeDesktopTransport.kt` — the replay client.
  - `android/app/src/main/kotlin/com/xu/wake/ui/web/WebReplayCodec.kt` — marker/mime helpers.
  - The desktop WebView settings in `android/app/src/main/kotlin/com/xu/wake/ui/Browser.kt` (desktop UA, wide-layout viewport, initial scale, third-party cookies).
- What was deliberately changed: the replay client is `HttpURLConnection`, not Ktor, so no HTTP-client dependency is added; ids use the app's own marker pair (`X-Xwake-Replay` / `_xwake_replay`) instead of the upstream `X-WebView-Post-Id` / `_webview_post_id`; the payload stash is an instance-scoped map drained through a callback instead of a process-wide static registry; redirects are followed by the app so only the main frame needs the JavaScript hop; cookies are shared through a `java.net.CookieHandler` bridge instead of copied by hand; response bodies are streamed into a capped buffer of 12 MB instead of handed over as a live stream; a request already running on the UI thread is moved to a single worker thread, so a replay can never block the UI; and the upstream JS dialog bridge (`showAlert`/`showPrompt`/`saveImportedCourses`/…) is **not** used, because this app extracts and previews the timetable itself.
- Attribution notice: the upstream license text is distributed in `android/app/src/main/assets/licenses/shiguangschedule-Apache-2.0.txt`; the adapted files carry a prominent notice of the change in their header comments, as Apache-2.0 section 4(b) requires.
- No upstream `NOTICE` file was present in this source checkout.

## AndroidLiquidGlass — minimal rounded-rectangle refraction algorithm

- Author / copyright: **Copyright 2025 Kyant**.
- Upstream: https://github.com/Kyant0/AndroidLiquidGlass
- Exact source commit: `65ab177e90e5c1d8c62e70cf7755841982da65f6`.
- License: **Apache License, Version 2.0**; the complete upstream license, including its copyright appendix, is distributed in `android/app/src/main/assets/licenses/AndroidLiquidGlass-Apache-2.0.txt`.
- Source consulted: `backdrop/src/commonMain/kotlin/com/kyant/backdrop/internal/Shaders.kt`, `effects/Lens.kt`, `backdrops/LayerBackdrop.kt`, `backdrops/LayerBackdropModifier.kt`, `internal/LayerRecorder.kt`, and Android `internal/RenderEffect.kt`.
- Adapted code: only the rounded-rectangle signed-distance / edge-normal / circular refraction algorithm in `android/app/src/main/kotlin/com/xu/wake/ui/glass/Glass.kt`. The original copyright and Apache notice are retained beside the shader. Changes are prominently identified there: a single rounded radius, protected zero-gradient calculation, padded local coordinates, and directional rim highlight combined with the lens shader.
- The capture/coordinate plumbing is a small Android-only Compose implementation written for Xwake after reviewing the upstream dependency graph. No upstream modules, sample app, cross-platform abstractions, custom shape library, dispersion, shadow machinery, or build dependency has been imported.
- No upstream `NOTICE` file was present in this source checkout.

### Runtime and integration boundaries

`GlassHost` scopes one live Compose background layer. Apply `Modifier.glassSource()` once to the page/background, excluding the dock and all glass consumers. Draw the dock as a later sibling in the same host. The source and consumer must share the Compose coordinate hierarchy; transformed ancestors (rotation/nonuniform scale) are not supported. Compose `SurfaceView`, video, other windows and platform window content cannot be sampled.

On Android 13+ and a hardware-accelerated canvas the liquid dock samples that recorded layer, applies a small blur and edge refraction using `RuntimeShader`, and adds a directional rim highlight. The indicator is a second local lens into the same original page, not a nested blur of its labels. There are no bitmap readbacks, screenshot permissions or always-running capture coroutine. The recorded page is updated through Compose drawing.

On Android 12 and older, software canvases, unavailable/absent capture, or shader/effect creation failure, the dock uses the opaque `surfaceContainerHigh` / `primaryContainer` frosted-material fallback. That path does **not** claim actual backdrop blur/refraction. Flat-color source pages naturally provide little visible optical distortion. GPU driver failures outside synchronous API calls still need device validation.
