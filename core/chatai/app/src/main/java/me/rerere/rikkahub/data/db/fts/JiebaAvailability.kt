package me.rerere.rikkahub.data.db.fts

/**
 * 进程级标记，记录 jieba 原生扩展 `libsimple.so` 是否可用。
 *
 * - `available = true`：在 [me.rerere.rikkahub.di.dataSourceModule] 里探测到
 *   `applicationInfo.nativeLibraryDir/libsimple` 存在，Requery SQLite 注册了
 *   自定义扩展，Room `onOpen` 回调里也会调用 `jieba_dict(?)` 把词典路径塞进
 *   连接。这时 `MessageFtsManager.search` 可以走 `jieba_query(?)` 拿到中文
 *   分词后的 MATCH 表达式。
 * - `available = false`（默认）：仓里没有产出 `libsimple.so` 的原生构建目标，
 *   APK 内不存在这个 .so，强行 `dlopen` 会让 `SQLiteConnection.open` 抛
 *   `SQLiteException: Could not register extension`。在这种情况下我们根本
 *   不注册扩展，搜索必须回退到内置 FTS5 简单分词（`text MATCH ?`），否则
 *   还是会撞 `no such function: jieba_query`。
 *
 * 写成一个独立 object 而不是塞到 Koin 里是因为：探测发生在
 * `dataSourceModule` 的 `single { AppDatabase }` 工厂体里，而搜索是 IO
 * 协程里跑的，如果走 DI 拿这个标志就要先 init 完 AppDatabase 才能用，
 * 顺序耦合；放成静态状态 + lazy 读一次，逻辑最直接。
 */
object JiebaAvailability {
    @Volatile
    var available: Boolean = false
}
