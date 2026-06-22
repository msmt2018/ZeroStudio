# logwire

A tiny, dependency-free library containing the wire protocol used by
ZeroStudio's `ide-log-plugin` AAR and the IDE's log receiver service.

## Why a separate module?

The protocol must be byte-for-byte identical on both sides. Hosting it
in a separate module ensures that the IDE and the host application
compile against the same Java types and constants. It is a 1-file
module on purpose: anything more than 200 lines of code here is a
design error.
