/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  SocksControlTransact: 走 IBinder.transact 自定义协议跟 host 端
 *  IdeShizukuSocksUserService 通信, 用于 Shizuku Socks 子路径。
 *
 *  协议 (跟 host 端 IdeShizukuSocksUserService.socksControlBinder 对应):
 *    - CODE_SET_SOCKS_PORT (1): 设 host 端 SOCKS5 server 监听端口 (0 = OS 选随机),
 *                              reply 写 int actualPort
 *    - CODE_GET_SOCKS_PORT (2): 取 host 端 SOCKS5 server actual port (没启返 -1)
 *    - CODE_STOP_SOCKS    (3): 停 host 端 SOCKS5 server
 *
 *  Interface token: 走 IdeShizukuSocksUserService.DESCRIPTOR, enforceInterface
 *  防 binder 错连别的 user service。
 *
 *  Phase 12y: 替代 Shizuku 13+ `UserServiceArgs.forAdd(Bundle)` 传 port 的方案
 *  (Shizuku 13.1.5 没 .args(Bundle) API, Bundle 字段是 private 不可加 user-supplied
 *  extras, 走 binder transact 是唯一通路)。
 *
 *  Phase 13c: 跟 Socks 路径 lifecycle 完善合并实装, stopSocks 兜底在 detach
 *  / IDE 端 release 时调。
 */

package com.itsaky.androidide.debugger.connection.shizuku

import android.os.IBinder
import android.os.Parcel
import com.itsaky.androidide.utils.ILogger
import java.io.IOException

class SocksControlTransact {

    private val log = ILogger.ROOT

    /**
     * 启 host 端 SOCKS5 server 在 127.0.0.1:requestedPort (0 = OS 选随机),
     * 返 host 端 actual port。
     *
     * 失败: 抛 IOException (host 端 reply 写 exception 的话, 走 readException 抛回;
     *       transact 本身失败抛 DeadObjectException 或 RuntimeException)。
     */
    fun setSocksPort(binder: IBinder, requestedPort: Int): Int {
        require(requestedPort in 0..65535) {
            "requestedPort must be 0..65535 (got $requestedPort)"
        }
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeInt(requestedPort)
            val rc = binder.transact(CODE_SET_SOCKS_PORT, data, reply, 0)
            if (!rc) {
                throw IOException("SocksControl: setSocksPort transact returned false")
            }
            reply.readException()  // host 端写异常时抛回
            reply.readInt()
        } catch (t: Throwable) {
            log.warn("SocksControl.setSocksPort($requestedPort) failed: {}", t.message)
            throw when (t) {
                is IOException -> t
                else -> IOException("SocksControl.setSocksPort failed: ${t.message}", t)
            }
        } finally {
            runCatching { data.recycle() }
            runCatching { reply.recycle() }
        }
    }

    /**
     * 取 host 端 SOCKS5 server actual port (没启返 -1)。
     * 主要用于诊断, IDE 端正常 attach 路径走 setSocksPort 拿 actual port。
     */
    fun getSocksPort(binder: IBinder): Int {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            val rc = binder.transact(CODE_GET_SOCKS_PORT, data, reply, 0)
            if (!rc) -1 else {
                reply.readException()
                reply.readInt()
            }
        } catch (t: Throwable) {
            log.warn("SocksControl.getSocksPort failed: {}", t.message)
            -1
        } finally {
            runCatching { data.recycle() }
            runCatching { reply.recycle() }
        }
    }

    /**
     * 停 host 端 SOCKS5 server。失败静默 (detach 时 binder 可能已死)。
     */
    fun stopSocks(binder: IBinder?) {
        if (binder == null) return
        // 优先 pingBinder, 死了不调 transact 避免 binder 死锁
        if (!binder.pingBinder()) {
            log.debug("SocksControl.stopSocks: binder dead, skip transact")
            return
        }
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            binder.transact(CODE_STOP_SOCKS, data, reply, 0)
            runCatching { reply.readException() }  // 静默: detach 阶段不应抛
            log.debug("SocksControl.stopSocks: ok")
        } catch (t: Throwable) {
            log.debug("SocksControl.stopSocks failed: {}", t.message)
        } finally {
            runCatching { data.recycle() }
            runCatching { reply.recycle() }
        }
    }

    companion object {
        const val CODE_SET_SOCKS_PORT = 1
        const val CODE_GET_SOCKS_PORT = 2
        const val CODE_STOP_SOCKS = 3
        // 必须跟 host 端 IdeShizukuSocksUserService.DESCRIPTOR 完全一致,
        // enforceInterface 用, 防止 binder 错连别的 user service。
        const val DESCRIPTOR =
            "com.itsaky.androidide.zerostudio.ide.debugger.host.IdeShizukuSocksUserService.ISocksControl"
    }
}
