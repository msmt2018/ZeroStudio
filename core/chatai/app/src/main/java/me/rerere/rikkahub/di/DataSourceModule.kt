package me.rerere.rikkahub.di

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.http.HttpHeaders
import io.pebbletemplates.pebble.PebbleEngine
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import io.requery.android.database.sqlite.SQLiteCustomExtension
import kotlinx.serialization.json.Json
import me.rerere.ai.provider.ProviderManager
import me.rerere.common.http.AcceptLanguageBuilder
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.data.ai.AIRequestInterceptor
import me.rerere.rikkahub.data.ai.RequestLoggingInterceptor
import me.rerere.rikkahub.data.ai.transformers.AssistantTemplateLoader
import me.rerere.rikkahub.data.ai.GenerationHandler
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.api.RikkaHubAPI
import me.rerere.rikkahub.data.api.SponsorAPI
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.fts.FtsAvailability
import me.rerere.rikkahub.data.db.fts.JiebaAvailability
import me.rerere.rikkahub.data.db.fts.MessageFtsManager
import me.rerere.rikkahub.data.db.fts.SimpleDictManager
import me.rerere.rikkahub.data.db.migrations.Migration_6_7
import me.rerere.rikkahub.data.db.migrations.Migration_11_12
import me.rerere.rikkahub.data.db.migrations.Migration_13_14
import me.rerere.rikkahub.data.db.migrations.Migration_14_15
import me.rerere.rikkahub.data.db.migrations.Migration_15_16
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.sync.webdav.WebDavSync
import me.rerere.search.SearchService
import me.rerere.rikkahub.data.sync.S3Sync
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val TAG = "DataSourceModule"

val dataSourceModule = module {
    single {
        SettingsStore(context = get(), scope = get())
    }

    single {
        val context: Context = get()
        // jieba 分词原生扩展 libsimple.so 是 jieba 搜索的依赖, 但仓里
        // 没有任何 CMake/ndk-build 目标会产出这个 .so, 默认情况下不会被打
        // 进 APK。如果硬编码把 SQLiteCustomExtension 注册进去, openInner
        // 阶段会直接 dlopen 失败抛 SQLiteException:
        //   "Could not register extension: dlopen failed: library
        //    '/data/app/.../libsimple.so' not found"
        // 这里改成运行时探测: 只有当 .so 真在 nativeLibraryDir 下时才注册
        // 扩展并调用 jieba_dict(?); 否则使用原版 Room openHelperFactory
        // 走内置 FTS5 简单分词, 搜索功能可用, 只是没有 jieba 词典能力。
        val libsimplePath = context.applicationInfo.nativeLibraryDir + "/libsimple"
        val jiebaAvailable = File(libsimplePath).exists().also {
            Log.i(TAG, "jieba extension libsimple.so present=$it (path=$libsimplePath)")
        }
        // 把探测结果存到全局, 给 MessageFtsManager.search() 用, 让搜索在没有
        // jieba 时回退到普通 MATCH, 避免 "no such function: jieba_query"。
        // (Static state 是因为 SQLiteDatabase 本身在进程内单例, 探测结果
        // 在 onCreate 阶段确定一次就够, 不用每次都再读一次文件系统。)
        JiebaAvailability.available = jiebaAvailable

        val builder = Room.databaseBuilder(context, AppDatabase::class.java, "rikka_hub")
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addMigrations(
                Migration_6_7,
                Migration_11_12,
                Migration_13_14,
                Migration_14_15,
                Migration_15_16,
            )

        if (jiebaAvailable) {
            builder.openHelperFactory(
                RequerySQLiteOpenHelperFactory(
                    listOf(
                        RequerySQLiteOpenHelperFactory.ConfigurationOptions { options ->
                            options.customExtensions.add(
                                SQLiteCustomExtension(libsimplePath, null)
                            )
                            options
                        },
                    )
                )
            )
        }

        builder
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    // 探测 FTS5 虚拟表模块是否可用.
                    // 部分设备/ROM 的 SQLite 编译时没开 FTS5, 这种情况下
                    // CREATE VIRTUAL TABLE ... USING fts5(...) 会抛
                    //   SQLiteException: no such module: fts5
                    // 而 onOpen 是 Room 打开数据库必经的回调, 抛了就把
                    // 整个 app 拖崩. 因此先用一个一次性小表探测, 失败就
                    // 走降级: 不建 message_fts, MessageFtsManager 整体
                    // no-op, 搜索框返回空列表, app 其它功能照常.
                    val fts5Available = runCatching {
                        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS _fts5_probe USING fts5(x)")
                        db.execSQL("DROP TABLE IF EXISTS _fts5_probe")
                        true
                    }.getOrElse {
                        Log.w(TAG, "FTS5 module not available; message search will be disabled", it)
                        false
                    }
                    FtsAvailability.available = fts5Available

                    if (fts5Available) {
                        // 即便 FTS5 可用, 建表这一步也兜一层 runCatching,
                        // 防止个别 SQLite 把"建表成功"和"索引可见"分成两
                        // 件事时, 后续的 INSERT/MATCH 报奇怪错, 而我们已经
                        // 把探测结果固化到 FtsAvailability, 建表失败不会影响
                        // DB 打开.
                        runCatching {
                            db.execSQL(
                                """
                                CREATE VIRTUAL TABLE IF NOT EXISTS message_fts USING fts5(
                                    text,
                                    node_id UNINDEXED,
                                    message_id UNINDEXED,
                                    conversation_id UNINDEXED,
                                    title UNINDEXED,
                                    update_at UNINDEXED,
                                    tokenize = 'simple'
                                )
                                """.trimIndent()
                            )
                        }.onFailure {
                            Log.w(TAG, "Failed to create message_fts table; search disabled", it)
                            FtsAvailability.available = false
                        }
                    } else {
                        Log.w(TAG, "Skipping message_fts table creation since FTS5 is not available")
                    }

                    if (jiebaAvailable) {
                        // 装入 jieba 词典路径, 让 FTS5 MATCH 时能通过
                        // jieba_query(?) 调用 jieba 切词。失败仅记日志,
                        // 仍然不阻断 DB 打开。
                        runCatching {
                            val dictDir = SimpleDictManager.extractDict(context)
                            val cursor = db.query(
                                "SELECT jieba_dict(?)",
                                arrayOf(dictDir.absolutePath),
                            )
                            cursor.use {
                                if (it.moveToFirst()) {
                                    val result = it.getString(0)
                                    val success =
                                        result?.trimEnd('/') == dictDir.absolutePath.trimEnd('/')
                                    if (!success) {
                                        Log.e(
                                            TAG,
                                            "jieba_dict failed: $result, path=${dictDir.absolutePath}",
                                        )
                                    }
                                }
                            }
                        }.onFailure {
                            Log.w(TAG, "jieba_dict registration failed; search will fall back", it)
                        }
                    }
                }
            })
            .build()
    }

    single {
        AssistantTemplateLoader(settingsStore = get())
    }

    single {
        PebbleEngine.Builder()
            .loader(get<AssistantTemplateLoader>())
            .defaultLocale(Locale.getDefault())
            .autoEscaping(false)
            .build()
    }

    single { TemplateTransformer(engine = get(), settingsStore = get()) }

    single {
        get<AppDatabase>().conversationDao()
    }

    single {
        get<AppDatabase>().memoryDao()
    }

    single {
        get<AppDatabase>().genMediaDao()
    }

    single {
        get<AppDatabase>().messageNodeDao()
    }

    single {
        get<AppDatabase>().managedFileDao()
    }

    single {
        get<AppDatabase>().favoriteDao()
    }

    single {
        get<AppDatabase>().workspaceDao()
    }

    single {
        MessageFtsManager(get())
    }

    single { McpManager(settingsStore = get(), appScope = get(), filesManager = get()) }

    single {
        GenerationHandler(
            context = get(),
            providerManager = get(),
            json = get(),
            memoryRepo = get(),
            conversationRepo = get()
        )
    }

    single<OkHttpClient> {
        val acceptLang = AcceptLanguageBuilder.fromAndroid(get())
            .build()
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .writeTimeout(120, TimeUnit.SECONDS)
            .followSslRedirects(true)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestBuilder = originalRequest.newBuilder()
                    .addHeader(HttpHeaders.AcceptLanguage, acceptLang)

                if (originalRequest.header(HttpHeaders.UserAgent) == null) {
                    requestBuilder.addHeader(HttpHeaders.UserAgent, "RikkaHub-Android/${BuildConfig.VERSION_NAME}")
                }

                chain.proceed(requestBuilder.build())
            }
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                val contentTypeHeader = request.header("Content-Type")
                if (
                    contentTypeHeader != null &&
                        contentTypeHeader.contains(";") &&
                        contentTypeHeader.substringBefore(";").trim().equals("application/json", ignoreCase = true)
                ) {
                    chain.proceed(
                        request.newBuilder()
                            .header("Content-Type", contentTypeHeader.substringBefore(";").trim())
                            .build()
                    )
                } else {
                    chain.proceed(request)
                }
            }
            .addNetworkInterceptor(RequestLoggingInterceptor())
            .addInterceptor(AIRequestInterceptor(remoteConfig = get()))
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            })
            .build().also { SearchService.init(it, get()) }
    }

    single {
        SponsorAPI.create(get())
    }

    single {
        ProviderManager(client = get(), context = get())
    }

    single {
        WebDavSync(
            settingsStore = get(),
            json = get(),
            context = get(),
            httpClient = get()
        )
    }

    single<HttpClient> {
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(20, TimeUnit.SECONDS)
                    readTimeout(10, TimeUnit.MINUTES)
                    writeTimeout(120, TimeUnit.SECONDS)
                    followSslRedirects(true)
                    followRedirects(true)
                    retryOnConnectionFailure(true)
                }
            }
        }
    }

    single {
        S3Sync(
            settingsStore = get(),
            json = get(),
            context = get(),
            httpClient = get()
        )
    }

    single<Retrofit> {
        Retrofit.Builder()
            .baseUrl("https://api.rikka-ai.com")
            .addConverterFactory(get<Json>().asConverterFactory("application/json; charset=UTF8".toMediaType()))
            .build()
    }

    single<RikkaHubAPI> {
        get<Retrofit>().create(RikkaHubAPI::class.java)
    }
}
