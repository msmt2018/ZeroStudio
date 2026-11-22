# 架构图与时序图

## 1. 进程拓扑

```mermaid
flowchart LR
  subgraph APP["ZeroStudio Android App Process"]
    GBS["GradleBuildService"]
    RUN["ToolingServerRunner"]
    API["Tooling API Facade"]
    DTOA["DTO / Model Cache"]
    GBS --> RUN
    GBS --> API
    API --> DTOA
  end

  subgraph JVM["Tooling JVM Child Process"]
    MAIN["Main"]
    DISPATCH["Message Dispatcher"]
    HANDLER["Tooling Command Handler"]
    PROJECT["AGP / Gradle Model Projector"]
    FILES["FileReference Resolver"]
    MAIN --> DISPATCH --> HANDLER
    HANDLER --> PROJECT
    HANDLER --> FILES
  end

  RUN <-->|"stdin / stdout: framed Protobuf"| MAIN
  RUN -->|"stderr: logs only"| LOG["Parent stderr reader"]
  PROJECT -->|"AGP / Gradle objects stay local"| GD["Gradle Tooling API"]
```

## 2. 双向管道与线程模型

```mermaid
flowchart TB
  subgraph P["Parent process"]
    PQ["Bounded outbound queue"]
    PW["Single pipe writer"]
    PR["Single pipe reader"]
    PD["Dispatcher / pending requests"]
  end
  subgraph C["Tooling JVM"]
    CR["Single pipe reader"]
    CD["Dispatcher / command handlers"]
    CQ["Bounded outbound queue"]
    CW["Single pipe writer"]
  end
  PQ --> PW
  PW -->|"Process.outputStream -> child stdin"| CR
  CR --> CD
  CD --> CQ
  CQ --> CW
  CW -->|"child stdout -> Process.inputStream"| PR
  PR --> PD
```

## 3. 消息生命周期

```mermaid
sequenceDiagram
  participant A as Android parent
  participant W as Parent writer
  participant R as Tooling reader
  participant H as Tooling handler
  participant G as Gradle/AGP model
  participant C as Tooling writer

  A->>W: Command(request_id)
  W->>R: length + Protobuf Envelope
  R->>H: decoded command
  H->>G: query / execute
  G-->>H: result or model data
  H->>C: Response / ModelBatch
  C-->>A: framed Protobuf
  Note over A,C: request_id correlates response; no generic remote object proxy
```

## 4. 文件路径选择

```mermaid
flowchart TD
  S["Need file on peer side"] --> Q{"Can peer access same canonical file path?"}
  Q -->|"Yes"| REF["Send FileReference metadata"]
  REF --> VALID["Validate root/path/version/size"]
  VALID --> OPEN["Peer opens file directly"]
  Q -->|"No"| COPY["Start FileTransfer"]
  COPY --> CHUNK["Bounded Protobuf chunks"]
  CHUNK --> WINDOW["Byte-window / backpressure"]
  WINDOW --> WRITE["Write temporary file"]
  WRITE --> VERIFY["Verify size and optional digest"]
  VERIFY --> ATOMIC["Atomic move to destination"]
```

## 5. 模型投影与分批

```mermaid
flowchart LR
  AGP["AGP / Gradle objects"] --> PROJ["Projector inside Tooling JVM"]
  PROJ --> DTO["Stable DTOs"]
  DTO --> BATCH["Bounded ModelBatch"]
  BATCH --> PB["Protobuf encode"]
  PB --> PIPE["Process pipe"]
  PIPE --> DECODE["Protobuf decode"]
  DECODE --> CACHE["Android DTO cache"]
  Q["Query only requested project/domain"] --> PROJ
```

## 6. 失败处理

```mermaid
stateDiagram-v2
  [*] --> Starting
  Starting --> Handshaking
  Handshaking --> Ready
  Handshaking --> Failed: version mismatch / invalid frame
  Ready --> Busy: request accepted
  Busy --> Ready: response/event completed
  Ready --> Closing: shutdown requested
  Busy --> Closing: cancel / process shutdown
  Closing --> Closed
  Ready --> Failed: EOF / broken pipe
  Busy --> Failed: child process exit
  Failed --> Closed
```
