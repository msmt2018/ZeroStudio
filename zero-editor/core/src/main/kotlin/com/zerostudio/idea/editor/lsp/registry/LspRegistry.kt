package com.zerostudio.idea.editor.lsp.registry

import android.content.Context
import com.zerostudio.idea.editor.lsp.connection.LspConnectionConfig
import com.zerostudio.idea.editor.lsp.model.LspServerInstance
import io.github.rosemoe.sora.lsp.requests.Timeouts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

abstract class LspServer {
    abstract val id: String
    abstract val languageName: String
    abstract val serverName: String
    abstract val supportedExtensions: List<String>
    open val customTimeouts: Map<Timeouts, Int> = emptyMap()

    private val _instances = MutableStateFlow<List<LspServerInstance>>(emptyList())
    val instances = _instances.asStateFlow()

    fun addInstance(instance: LspServerInstance) {
        _instances.update { it + instance }
    }

    fun removeInstance(instance: LspServerInstance) {
        _instances.update { it - instance }
    }

    abstract fun getConnectionConfig(): LspConnectionConfig
    abstract suspend fun isInstalled(context: Context): Boolean
}

class ExternalLspServer(
    override val id: String,
    override val languageName: String,
    override val serverName: String,
    override val supportedExtensions: List<String>,
    val connectionType: String, // "socket" or "process"
    val host: String = "localhost",
    val port: Int = 0,
    val command: String = ""
) : LspServer() {

    override fun getConnectionConfig(): LspConnectionConfig {
        return if (connectionType == "socket") {
            LspConnectionConfig.Socket(host, port)
        } else {
            LspConnectionConfig.Process(command.split(" ").toTypedArray())
        }
    }

    override suspend fun isInstalled(context: Context): Boolean = true
}

object LspRegistry {
    private const val PERSISTENCE_FILE = "external_lsp_servers.json"

    private val _builtInServers = MutableStateFlow<List<LspServer>>(emptyList())
    val builtInServers = _builtInServers.asStateFlow()

    private val _externalServers = MutableStateFlow<List<ExternalLspServer>>(emptyList())
    val externalServers = _externalServers.asStateFlow()

    fun registerBuiltInServer(server: LspServer) {
        _builtInServers.update { list -> if (list.none { it.id == server.id }) list + server else list }
    }

    fun addExternalServer(context: Context, server: ExternalLspServer) {
        _externalServers.update { it + server }
        saveExternalServers(context)
    }

    fun removeExternalServer(context: Context, serverId: String) {
        _externalServers.update { list -> list.filterNot { it.id == serverId } }
        saveExternalServers(context)
    }

    fun findServerForExtension(ext: String): LspServer? {
        val lowerExt = ext.lowercase()
        return _externalServers.value.find { it.supportedExtensions.contains(lowerExt) }
            ?: _builtInServers.value.find { it.supportedExtensions.contains(lowerExt) }
    }

    fun loadExternalServers(context: Context) {
        val file = File(context.filesDir, PERSISTENCE_FILE)
        if (!file.exists()) return

        runCatching {
            val jsonArray = JSONArray(file.readText())
            val list = mutableListOf<ExternalLspServer>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val exts = mutableListOf<String>()
                val extsArray = obj.getJSONArray("extensions")
                for (j in 0 until extsArray.length()) {
                    exts.add(extsArray.getString(j))
                }
                list.add(
                    ExternalLspServer(
                        id = obj.getString("id"),
                        languageName = obj.getString("languageName"),
                        serverName = obj.getString("serverName"),
                        supportedExtensions = exts,
                        connectionType = obj.getString("connectionType"),
                        host = obj.optString("host", "localhost"),
                        port = obj.optInt("port", 0),
                        command = obj.optString("command", "")
                    )
                )
            }
            _externalServers.value = list
        }
    }

    private fun saveExternalServers(context: Context) {
        val file = File(context.filesDir, PERSISTENCE_FILE)
        val array = JSONArray()
        for (server in _externalServers.value) {
            val obj = JSONObject().apply {
                put("id", server.id)
                put("languageName", server.languageName)
                put("serverName", server.serverName)
                put("connectionType", server.connectionType)
                put("host", server.host)
                put("port", server.port)
                put("command", server.command)
                put("extensions", JSONArray(server.supportedExtensions))
            }
            array.put(obj)
        }
        file.writeText(array.toString())
    }
}