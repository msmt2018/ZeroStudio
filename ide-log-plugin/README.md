# ide-log-plugin

High-fidelity log + JDWP bridge plugin. Packaged as an AAR and injected
into the **debug variant** of every Android project built with
ZeroStudio.

This module replaces the legacy `plugin-api.jar` + `zerostudio-gradle-plugin-1.0.0.jar`
+ `logger-runtime.zip` triplet that was used in pre-2026 versions of
AndroidIDE.

## What it does

* Captures **all** log records (logcat, application logs, uncaught
  exceptions, ANR, native JNI lines) and ships them over a single TCP
  socket to the IDE.
* Starts an embedded **JDWP server** on a free port; the IDE discovers
  the port via the plugin's hello packet.
* Auto-installs via a `ContentProvider` so that the hook fires before
  the host application's `Application.onCreate()` runs.

## How it is injected

1. `:ide-log-plugin` is built as an AAR by the IDE build pipeline.
2. The AAR is copied into `data/common/ide-log-plugin-1.0.0.aar` in the
   IDE's assets.
3. At first launch the IDE extracts the AAR to
   `~/.androidide/ide-log-plugin/` and copies the inner `classes.jar`
   into the Gradle init classpath via `composite-builds/.../GenerateInitScriptTask`.
4. The user's debug-variant APK embeds the AAR as a normal
   `implementation` dependency and the IDE build manifest-merges the
   `IdeLogInstaller` `ContentProvider` into the host manifest.

## Wire format

The IDE and the host application exchange a small binary protocol
documented in [`utilities/logwire`](../utilities/logwire). The packet
header is:

```
[magic:int32][version:int32][type:uint8][length:int32][body:length bytes]
```

Magic = `0x4C505352` ("LPSR"), version = `1`, body length capped at
16 MB.

## Build

The module is built as a regular `com.android.library` Gradle module.
The `release` variant is what is copied into the IDE's assets.

## Status

* PR-1: capture pipeline + AAR integration (this module)
* PR-2: full JDWP server implementation (filling in `JdwpServer.java`)
