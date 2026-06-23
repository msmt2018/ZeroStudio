# Contributing

This document outlines how to propose a change to AndroidIDE. By contributing to this project, you
agree to abide the terms specified in the [CODE OF CONDUCT](./CODE_OF_CONDUCT.md).

## Requirements

- Android Studio.
- JDK 17 (prefer the one bundled with Android Studio).

> NOTE:
>
> At the time of this writing, the stable version of Android Studio (Electric Eel) is bundled with
> JDK 11. Only Android Studio Flamingo and newer are bundled with JDK 17.

## Build the project

Open the project in Android Studio. No extra steps are needed for this.

It is possible to build AndroidIDE in AndroidIDE itself. But, as it might be a too heavy task for an
Android device to be able to build the project, Android Studio is always recommended.

You can refer the [AndroidIDE Developer Guide](https://docs.androidide.com/developer/index.html) to
get started with the development or to learn more about the various components in the IDE. 

## Source code format

- Indents : 2-space
- Java : `GoogleStyle`. Either use `google-java-format` or
  import [this](https://raw.githubusercontent.com/google/styleguide/gh-pages/intellij-java-google-style.xml)
  code style.
- Kotlin: Use [`ktfmt`](https://plugins.jetbrains.com/plugin/14912-ktfmt) IntelliJ Plugin and set
  the code style to `Google (internal)`
  . [`Learn more`](https://github.com/facebook/ktfmt#intellij-android-studio-and-other-jetbrains-ides)
  .
- XML : Default Android Studio formatter with 2-space indentations.

## Propose a change

Before proposing a change, it is always recommended to open an issue and discuss it with the
maintainers.

**The `main` branch is protected and requires all the commits to be signed with your GPG key and the
commit history to be linear.**
See [protected branches](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/defining-the-mergeability-of-pull-requests/about-protected-branches)
.

To contribute to this project,

- Fork the repo.
- Clone the forked repo to your local machine.
- Open the project.
- Make your changes.
- Create a pull request. Give a proper title and description to your PR.

## Issues & Pull requests

Use the issue/PR templates whenever possible. Provide a proper title and a clear description of the
issue/PR. For bug reports, provide a step-by-step procedure to reproduce the issue. Always put
things like crash
logs, and build outputs in `code blocks`.

## Contact us

If you want to discuss anything about the project, you can discuss it in
the [Telegram group](https://t.me/androidide_discussions).

## Debugger Development

The debugger system (ide-debugger module) has specific development guidelines:

### Architecture

The debugger consists of three layers:
1. **IDE Application** (core/app) - UI components
2. **Debugger Engine** (ide-debugger) - JDWP protocol implementation
3. **Target App** (ide-log-plugin) - JDWP server in debugged app

See [Architecture Documentation](./docs/architecture/ide-debugger-architecture.md) for details.

### JDWP Protocol

- JDWP 1.6 specification: https://docs.oracle.com/javase/8/docs/platform/jpda/jdwp-spec.html
- Packet structure: length(4) + id(4) + flags(1) + data(N)
- Command sets used: VirtualMachine(1), ReferenceType(2), Method(6), ThreadReference(11), etc.

### Testing

Run unit tests before submitting debugger changes:
```bash
gradle :ide-debugger:testDebugUnitTest
```

Tests cover:
- JdwpPacketCodec - Packet encoding/decoding
- BreakpointStore - Breakpoint CRUD
- DebugSession - State machine
- SourceLocator - Breakpoint installation paths
- EvalEngine - Expression evaluation

### Code Style for Debugger

- Use `@NonNull` / `@Nullable` annotations from androidx.annotation
- Keep JDWP packet handling in dedicated classes (JdwpPacket, JdwpPacketCodec)
- All public Debugger API methods must be thread-safe
- Use FakeJdwpClient for unit testing JDWP interactions
