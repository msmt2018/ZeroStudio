package me.rerere.rikkahub.data.db.fts

/**
 * 进程级标记，记录 Room 用的 SQLite 连接是否提供 FTS5 虚拟表模块。
 *
 * - `available = true`：在 [me.rerere.rikkahub.di.dataSourceModule] 的
 *   `RoomDatabase.Callback.onOpen` 里探测到当前 SQLite 能成功执行
 *   `CREATE VIRTUAL TABLE ... USING fts5(...)`，`message_fts` 表被建出来，
 *   [MessageFtsManager] 的 index / search / delete 可以正常工作。
 * - `available = false`（默认）：当前 SQLite 编译时没开 FTS5，强行
 *   `CREATE VIRTUAL TABLE ... USING fts5(...)` 会抛
 *   `SQLiteException: no such module: fts5`，而 `onOpen` 是 Room 打开数据库
 *   必经的回调，抛了就直接把 App 拖崩。
 *
 * 命中 `false` 时我们**不**建 `message_fts` 表，
 * [MessageFtsManager] 也整体降级为 no-op：搜索返回空，写入/删除跳过。
 * 业务侧 `SearchPage` / `SearchVM` 拿到空列表时 UI 仍然能渲染（显示
 * "没有匹配项" 之类的空态），app 不会因为搜索框而打不开数据库。
 *
 * 写成一个独立 object 而不是塞到 Koin 里有两个原因：
 * 1. 探测发生在 `dataSourceModule` 的 `single { AppDatabase }` 工厂体里；
 *    `SearchPage` / `SearchVM` 不应该为了拿一个布尔标志而拖进 AppDatabase
 *    的构造顺序里。
 * 2. Room 进程内单例 + 探测在 onCreate 阶段只跑一次，结果不会变，用
 *    `@Volatile` 静态状态足够，逻辑最直接。
 */
object FtsAvailability {
    @Volatile
    var available: Boolean = false
}
