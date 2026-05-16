/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.itsaky.androidide.services.builder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.text.TextUtils
import androidx.core.app.NotificationManagerCompat
import com.blankj.utilcode.util.ResourceUtils
import com.blankj.utilcode.util.ZipUtils
import ch.epfl.scala.bsp4j.BuildServer
import ch.epfl.scala.bsp4j.ExitBuildParams
import ch.epfl.scala.bsp4j.InitializeBuildParams
import ch.epfl.scala.bsp4j.OnBuildInitializedParams
import com.itsaky.androidide.BuildConfig
import com.itsaky.androidide.R.*
import com.itsaky.androidide.app.BaseApplication
import com.itsaky.androidide.lookup.Lookup
import com.itsaky.androidide.managers.ToolsManager
import com.itsaky.androidide.preferences.internal.BuildPreferences
import com.itsaky.androidide.preferences.internal.DevOpsPreferences
import com.itsaky.androidide.projects.builder.BuildService
import com.itsaky.androidide.projects.internal.ProjectManagerImpl
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.services.ToolingServerNotStartedException
import com.itsaky.androidide.services.builder.ToolingServerRunner.OnServerStartListener
import com.itsaky.androidide.tasks.ifCancelledOrInterrupted
import com.itsaky.androidide.tasks.runOnUiThread
import com.itsaky.androidide.tooling.api.ForwardingToolingApiClient
import com.itsaky.androidide.tooling.api.IToolingApiClient
import com.itsaky.androidide.tooling.api.IToolingApiServer
import com.itsaky.androidide.tooling.api.LogSenderConfig.PROPERTY_LOGSENDER_ENABLED
import com.itsaky.androidide.tooling.api.messages.InitializeProjectParams
import com.itsaky.androidide.tooling.api.messages.LogMessageParams
import com.itsaky.androidide.tooling.api.messages.result.BuildCancellationRequestResult
import com.itsaky.androidide.tooling.api.messages.result.BuildInfo
import com.itsaky.androidide.tooling.api.messages.result.BuildResult
import com.itsaky.androidide.tooling.api.messages.result.GradleWrapperCheckResult
import com.itsaky.androidide.tooling.api.messages.result.InitializeResult
import com.itsaky.androidide.tooling.api.messages.result.TaskExecutionResult
import com.itsaky.androidide.tooling.api.models.ToolingServerMetadata
import com.itsaky.androidide.tooling.events.ProgressEvent
import com.itsaky.androidide.utils.Environment
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment
import java.io.File
import java.io.InterruptedIOException
import java.io.IOException
import java.io.InputStream
import java.lang.ref.WeakReference
import java.util.Objects
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.Collections
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * A foreground service that handles interaction with the Gradle Tooling API.
 *
 * @author Akash Yadav
 */
class GradleBuildService :
    Service(), BuildService, IToolingApiClient, ToolingServerRunner.Observer {

  private var mBinder: GradleServiceBinder? = null
  private var isToolingServerStarted = false
  override var isBuildInProgress = false
    private set

  /**
   * We do not provide direct access to GradleBuildService instance to the Tooling API launcher as
   * it may cause memory leaks. Instead, we create another client object which forwards all calls to
   * us. So, when the service is destroyed, we release the reference to the service from this
   * client.
   */
  private var _toolingApiClient: ForwardingToolingApiClient? = null
  private var toolingServerRunner: ToolingServerRunner? = null
  private var outputReaderJob: Job? = null
  private var notificationManager: NotificationManager? = null
  private var server: IToolingApiServer? = null
  private var bspServer: BuildServer? = null
  private var eventListener: EventListener? = null
  private var isReleaseVariant = false

  @Volatile private var currentBuildProcess: Process? = null

  private val serviceJob = SupervisorJob()
  private val buildServiceScope =
      CoroutineScope(serviceJob + Dispatchers.Default + CoroutineName("GradleBuildService"))
  private val pendingBuildRequests = Collections.synchronizedSet(mutableSetOf<CompletableFuture<*>>())

  private val isGradleWrapperAvailable: Boolean
    get() {
      val projectManager = ProjectManagerImpl.getInstance()
      val projectDir = projectManager.projectDirPath
      if (TextUtils.isEmpty(projectDir)) {
        return false
      }

      val projectRoot = Objects.requireNonNull(projectManager.projectDir)
      if (!projectRoot.exists()) {
        return false
      }

      val gradlew = File(projectRoot, "gradlew")
      val gradleWrapperJar = File(projectRoot, "gradle/wrapper/gradle-wrapper.jar")
      val gradleWrapperProps = File(projectRoot, "gradle/wrapper/gradle-wrapper.properties")
      return gradlew.exists() && gradleWrapperJar.exists() && gradleWrapperProps.exists()
    }

  companion object {

    private val log = LoggerFactory.getLogger(GradleBuildService::class.java)
    private val NOTIFICATION_ID = R.string.app_name
    private val SERVER_System_err = LoggerFactory.getLogger("ToolingApiErrorStream")
  }

  override fun onCreate() {
    notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    showNotification(getString(R.string.build_status_idle), false)
    Lookup.getDefault().update(BuildService.KEY_BUILD_SERVICE, this)
  }

  override fun isToolingServerStarted(): Boolean {
    return isToolingServerStarted && server != null
  }

  private fun showNotification(
      message: String,
      @Suppress("SameParameterValue") isProgress: Boolean,
  ) {
    log.info("Showing notification to user...")
    createNotificationChannels()
    startForeground(NOTIFICATION_ID, buildNotification(message, isProgress))
  }

  private fun createNotificationChannels() {
    val buildNotificationChannel =
        NotificationChannel(
            BaseApplication.NOTIFICATION_GRADLE_BUILD_SERVICE,
            getString(string.title_gradle_service_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
    NotificationManagerCompat.from(this).createNotificationChannel(buildNotificationChannel)
  }

  private fun buildNotification(message: String, isProgress: Boolean): Notification {
    val ticker = getString(R.string.title_gradle_service_notification_ticker)
    val title = getString(R.string.title_gradle_service_notification)
    val launch = packageManager.getLaunchIntentForPackage(BuildConfig.APPLICATION_ID)
    val intent = PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT)
    val builder =
        Notification.Builder(this, BaseApplication.NOTIFICATION_GRADLE_BUILD_SERVICE)
            .setSmallIcon(R.drawable.ic_launcher_notification)
            .setTicker(ticker)
            .setWhen(System.currentTimeMillis())
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(intent)

    // Checking whether to add a ProgressBar to the notification
    if (isProgress) {
      // Add ProgressBar to Notification
      builder.setProgress(100, 0, true)
    }
    return builder.build()
  }

  override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
    // No point in restarting the service if it really gets killed.
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    // 【内存泄漏修复优化】：强制停止所有的后台 gradlew 进程。
    // 因为在 performBuildTasks 方法中，CompletableFuture 可能会因为底层进程僵死或还在执行导致 get() 方法无尽等待。
    // 如果我们在销毁时没有杀掉进程，ForkJoinPool 工作线程将会持有这个 Service 对象并永远被阻塞。
    killGradlewProcesses()

    mBinder?.release()
    mBinder = null

    log.info("Service is being destroyed. Dismissing the shown notification...")
    notificationManager!!.cancel(NOTIFICATION_ID)

    val lookup = Lookup.getDefault()
    lookup.unregister(BuildService.KEY_BUILD_SERVICE)
    lookup.unregister(BuildService.KEY_PROJECT_PROXY)

    server?.also { server ->
      try {
        log.info("Shutting down Tooling API server...")
        // send the shutdown request but do not wait for the server to respond
        // the service should not block the onDestroy call in order to avoid timeouts
        // the tooling server must release resources and exit automatically
        server.shutdown().get(1, TimeUnit.SECONDS)
      } catch (e: Throwable) {
        log.error("Failed to shutdown Tooling API server", e)
      }
    }
    bspServer?.also { bsp ->
      try {
        bsp.buildShutdown().get(1, TimeUnit.SECONDS)
        bsp.onBuildExit(ExitBuildParams())
      } catch (e: Throwable) {
        log.warn("Failed to shutdown BSP server", e)
      }
    }

    log.debug("Cancelling tooling server runner...")
    toolingServerRunner?.release()
    toolingServerRunner = null

    _toolingApiClient?.client = null
    _toolingApiClient = null

    log.debug("Cancelling tooling server output reader job...")
    outputReaderJob?.cancel()
    outputReaderJob = null

    synchronized(pendingBuildRequests) {
      pendingBuildRequests.forEach { it.cancel(true) }
      pendingBuildRequests.clear()
    }

    // Ensure the build process is terminated immediately upon Service destruction to prevent leaks
    currentBuildProcess?.destroy()
    currentBuildProcess = null
    serviceJob.cancel()

    isToolingServerStarted = false
    super.onDestroy()
  }

  override fun onBind(intent: Intent): IBinder? {
    if (mBinder == null) {
      mBinder = GradleServiceBinder(this)
    }
    return mBinder
  }

  /** Creates a Gradle init script that injects the logger plugin into user projects. */
  private fun createLoggerInitScript(): File {
    val initScript = File(Environment.TMP_DIR, "ide-logger-init.gradle")
    initScript.writeText(
        """
          allprojects {
              afterEvaluate {
                  if (plugins.hasPlugin('com.android.application') || 
                      plugins.hasPlugin('com.android.library')) {
                      
                      android {
                          compileOptions {
                              coreLibraryDesugaringEnabled = true
                          }
                      }
                      
                      dependencies {
                          implementation files('${getLoggerRuntimeAar().absolutePath}')
                          coreLibraryDesugaring 'com.android.tools:desugar_jdk_libs:2.0.4'
                      }
                  }
              }
          }
      """
            .trimIndent()
    )
    return initScript
  }

  /** Gets or creates the logger plugin directory. */
  private fun getLoggerPluginDir(): File {
    val dir = File(Environment.PLUGIN_HOME, "logger")
    if (!dir.exists()) {
      dir.mkdirs()
    }
    return dir
  }

  /** Extracts and returns the logger runtime AAR file. */
  private fun getLoggerRuntimeAar(): File {
    val aar = File(getLoggerPluginDir(), "logger-runtime.aar")
    if (!aar.exists()) {
      // Extract from assets
      if (
          !ResourceUtils.copyFileFromAssets(
              "data/common/logger-runtime.aar",
              aar.absolutePath,
          )
      ) {
        log.error("Failed to extract logger-runtime.aar from assets")
      }
    }
    return aar
  }

  /** Check if tasks include debug builds (not release-only). */
  private fun isDebugBuild(tasks: List<String>): Boolean {
    // Check if any task contains "Debug" or doesn't contain "Release"
    val hasDebugTask = tasks.any { task ->
      task.contains("Debug", ignoreCase = true) || task.contains("assembleDebug", ignoreCase = true)
    }

    val hasOnlyRelease = tasks.all { task ->
      task.contains("Release", ignoreCase = true) ||
          task.contains("assembleRelease", ignoreCase = true)
    }

    // If it's explicitly debug, or not explicitly release-only, treat as debug
    return hasDebugTask || !hasOnlyRelease
  }

  /**
   * Inject logger by adding init script to Gradle arguments. This modifies the system property that
   * will be read by the Tooling API.
   */
  private fun injectLoggerForCurrentBuild() {
    val initScript = createLoggerInitScript()
    // Set property that will be picked up by Tooling API
    System.setProperty("ide.logger.init.script", initScript.absolutePath)
  }

  override fun onListenerStarted(
      bspServer: BuildServer,
      errorStream: InputStream,
  ) {
    startServerOutputReader(errorStream)
    this.bspServer = bspServer
    isToolingServerStarted = true
  }

  override fun onServerExited(exitCode: Int) {
    log.warn("Tooling API process terminated with exit code: {}", exitCode)
    stopForeground(STOP_FOREGROUND_REMOVE)
  }

  override fun getClient(): IToolingApiClient {
    if (_toolingApiClient == null) {
      _toolingApiClient = ForwardingToolingApiClient(this)
    }
    return _toolingApiClient!!
  }

  override fun logMessage(params: LogMessageParams) {
    val logger = LoggerFactory.getLogger(params.tag)
    when (params.level) {
      'D' -> logger.debug(params.message)
      'W' -> logger.warn(params.message)
      'E' -> logger.error(params.message)
      'I' -> logger.info(params.message)

      else -> logger.trace(params.message)
    }
  }

  override fun logOutput(line: String) {
    eventListener?.onOutput(line)
  }

  override fun prepareBuild(buildInfo: BuildInfo) {
    updateNotification(getString(R.string.build_status_in_progress), true)
    eventListener?.prepareBuild(buildInfo)
  }

  override fun onBuildSuccessful(result: BuildResult) {
    updateNotification(getString(R.string.build_status_sucess), false)
    eventListener?.onBuildSuccessful(result.tasks)
    if (result.tasks.any { it.contains("assemble", true) || it.contains("bundle", true) }) {
      cleanupIdleResources("post-build").exceptionally {
        log.warn("Post-build runtime cleanup failed", it)
        false
      }
    }
  }

  override fun onBuildFailed(result: BuildResult) {
    updateNotification(getString(R.string.build_status_failed), false)
    eventListener?.onBuildFailed(result.tasks)
  }

  override fun onProgressEvent(event: ProgressEvent) {
    eventListener?.onProgressEvent(event)
  }

  override fun getBuildArguments(): CompletableFuture<List<String>> {
    val extraArgs = ArrayList<String>()

    if (DevOpsPreferences.logsenderEnabled) {
      injectLoggerForCurrentBuild()
      if (!isReleaseVariant) {
        val initScriptPath = System.getProperty("ide.logger.init.script")
        if (initScriptPath != null) {
          extraArgs.add("--init-script")
          extraArgs.add(initScriptPath)
          System.clearProperty("ide.logger.init.script")
        }
      }
    }

    // Override AAPT2 binary
    // The one downloaded from Maven is not built for Android
    extraArgs.add("-Pandroid.aapt2FromMavenOverride=" + Environment.AAPT2.absolutePath)
    extraArgs.add("-P${PROPERTY_LOGSENDER_ENABLED}=${DevOpsPreferences.logsenderEnabled}")
    if (BuildPreferences.isStacktraceEnabled) {
      extraArgs.add("--stacktrace")
    }
    if (BuildPreferences.isInfoEnabled) {
      extraArgs.add("--info")
    }
    if (BuildPreferences.isDebugEnabled) {
      extraArgs.add("--debug")
    }
    if (BuildPreferences.isScanEnabled) {
      extraArgs.add("--scan")
    }
    if (BuildPreferences.isWarningModeAllEnabled) {
      extraArgs.add("--warning-mode")
      extraArgs.add("all")
    }
    if (BuildPreferences.isBuildCacheEnabled) {
      extraArgs.add("--build-cache")
    }
    if (BuildPreferences.isOfflineEnabled) {
      extraArgs.add("--offline")
    }
    return CompletableFuture.completedFuture(extraArgs)
  }

  override fun checkGradleWrapperAvailability(): CompletableFuture<GradleWrapperCheckResult> {
    return if (isGradleWrapperAvailable)
        CompletableFuture.completedFuture(GradleWrapperCheckResult(true))
    else installWrapper()
  }

  internal fun setServerListener(listener: OnServerStartListener?) {
    if (toolingServerRunner != null) {
      toolingServerRunner!!.setListener(listener)
    }
  }

  private fun installWrapper(): CompletableFuture<GradleWrapperCheckResult> {
    eventListener?.also { eventListener ->
      eventListener.onOutput("-------------------- NOTE --------------------")
      eventListener.onOutput(getString(R.string.msg_installing_gradlew))
      eventListener.onOutput("----------------------------------------------")
    }
    return CompletableFuture.supplyAsync { doInstallWrapper() }
  }

  private fun doInstallWrapper(): GradleWrapperCheckResult {
    val extracted = File(Environment.TMP_DIR, "gradle-wrapper.zip")
    if (
        !ResourceUtils.copyFileFromAssets(
            ToolsManager.getCommonAsset("gradle-wrapper.zip"),
            extracted.absolutePath,
        )
    ) {
      log.error("Unable to extract gradle-plugin.zip from IDE resources.")
      return GradleWrapperCheckResult(false)
    }
    try {
      val projectDir = ProjectManagerImpl.getInstance().projectDir
      val files = ZipUtils.unzipFile(extracted, projectDir)
      if (files != null && files.isNotEmpty()) {
        return GradleWrapperCheckResult(true)
      }
    } catch (e: IOException) {
      log.error("An error occurred while extracting Gradle wrapper", e)
    }
    return GradleWrapperCheckResult(false)
  }

  private fun updateNotification(message: String, isProgress: Boolean) {
    runOnUiThread { doUpdateNotification(message, isProgress) }
  }

  private fun doUpdateNotification(message: String, isProgress: Boolean) {
    (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(
        NOTIFICATION_ID,
        buildNotification(message, isProgress),
    )
  }

  override fun metadata(): CompletableFuture<ToolingServerMetadata> {
    checkServerStarted()
    if (server == null && bspServer != null) {
      return CompletableFuture.completedFuture(
          ToolingServerMetadata(pid ?: -1)
      )
    }
    return server!!.metadata()
  }

  override fun initializeProject(
      params: InitializeProjectParams
  ): CompletableFuture<InitializeResult> {
    checkServerStarted()
    Objects.requireNonNull(params)
    if (server == null && bspServer != null) {
      val initialize = InitializeBuildParams()
      initialize.displayName = "AndroidIDE"
      initialize.version = BuildConfig.VERSION_NAME
      initialize.bspVersion = "2.2.0"
      initialize.rootUri = File(params.directory).toURI().toString()
      return performBuildTasks(bspServer!!.buildInitialize(initialize).thenApply {
        bspServer!!.onBuildInitialized(OnBuildInitializedParams())
        InitializeResult(true, null)
      })
    }
    return performBuildTasks(server!!.initialize(params)).thenApply { result ->
      if (result != null) {
        buildServiceScope.launch {
          try {
            kotlinx.coroutines.delay(5000) // 5 seconds
            log.info("5 seconds elapsed after initialization, stopping Gradle daemons...")
            // stopGradleDaemons().get()
          } catch (e: Exception) {
            log.error("Error in post-initialization daemon cleanup", e)
          }
        }
      }
      result
    }
  }

  /** Stops all Gradle daemons by executing gradlew --stop */
  private fun stopGradleDaemons(): CompletableFuture<Void> {
    return CompletableFuture.runAsync {
      try {
        val projectDir = ProjectManagerImpl.getInstance().projectDir
        val gradlewPath = File(projectDir, "gradlew").absolutePath

        log.info("Stopping Gradle daemons...")

        val command = listOf("sh", gradlewPath, "--stop")
        val processBuilder = ProcessBuilder(command)
        processBuilder.directory(projectDir)

        // Set up environment
        val termuxEnv = TermuxShellEnvironment().getEnvironment(this@GradleBuildService, false)
        val customEnv = HashMap<String, String>()
        Environment.putEnvironment(customEnv, false)

        val finalEnv = processBuilder.environment()
        finalEnv.putAll(termuxEnv)
        finalEnv.putAll(customEnv)

        val process = processBuilder.start()
        val exitCode = process.waitFor()

        if (exitCode == 0) {
          log.info("Gradle daemons stopped successfully")
          eventListener?.onOutput("Gradle daemons stopped")
        } else {
          log.warn("Failed to stop Gradle daemons, exit code: $exitCode")
        }
      } catch (e: Exception) {
        log.error("Error stopping Gradle daemons", e)
      }
    }
  }

  override fun executeTasks(vararg tasks: String): CompletableFuture<TaskExecutionResult> {
    checkServerStarted()
    val tasksList = tasks.toList()
    isReleaseVariant = false

    if (isDebugBuild(tasksList)) {
      log.info("Debug build detected, injecting logger plugin")
      injectLoggerForCurrentBuild()
    } else {
      log.info("Release build detected, skipping logger injection")
      isReleaseVariant = true
    }

    /*
    * @idea Mohammed-Baqer-Null @ https://github.com/Mohammed-baqer-null
    * ! THIS IS A TEMPORARY FIX ! gradually transforming acs lite compiler in here properly in v..04 or 05

    * - Using the local Gradle wrapper (gradlew) is significantly faster than using the Tooling API.
    * - Employing the Tooling API for compilation on Android is a poor choice this is a resource-limited Android environment, not a desktop one.
    * - The Tooling API consumes excessive JVM memory without delivering meaningful benefits.
    * - The implementation below resolves OutOfMemory exceptions seamlessly.
    */

    return performBuildTasks(
        CompletableFuture.supplyAsync {
          val buildInfo = BuildInfo(tasksList)
          prepareBuild(buildInfo)

          try {
            val projectDir = ProjectManagerImpl.getInstance().projectDir
            val gradlewPath = File(projectDir, "gradlew").absolutePath

            val command = mutableListOf("sh", gradlewPath)
            command.addAll(tasks)

            val buildArgs = getBuildArguments().get()
            command.addAll(buildArgs)

            log.info("Executing command: ${command.joinToString(" ")}")

            val processBuilder = ProcessBuilder(command)
            processBuilder.directory(projectDir)

            // Get Termux environment
            val termuxEnv = TermuxShellEnvironment().getEnvironment(this@GradleBuildService, false)

            // Add custom environment variables from Environment class
            val customEnv = HashMap<String, String>()
            Environment.putEnvironment(customEnv, false)

            // Merge environments
            val finalEnv = processBuilder.environment()
            finalEnv.putAll(termuxEnv)
            finalEnv.putAll(customEnv)

            // Ensure PATH includes BIN_DIR for clang, python, etc.
            val currentPath = finalEnv["PATH"] ?: ""
            val binDirPath = Environment.BIN_DIR.absolutePath
            val prefixBinPath = File(Environment.PREFIX, "bin").absolutePath

            // Add BIN_DIR and PREFIX/bin to PATH if not already present
            val pathEntries = mutableListOf<String>()
            if (!currentPath.contains(binDirPath)) {
              pathEntries.add(binDirPath)
            }
            if (!currentPath.contains(prefixBinPath)) {
              pathEntries.add(prefixBinPath)
            }
            pathEntries.add(currentPath)

            finalEnv["PATH"] = pathEntries.filter { it.isNotEmpty() }.joinToString(":")

            // Add LD_LIBRARY_PATH for native libraries
            val ldLibraryPath = finalEnv["LD_LIBRARY_PATH"] ?: ""
            val libDirPath = Environment.LIB_DIR.absolutePath
            finalEnv["LD_LIBRARY_PATH"] =
                if (ldLibraryPath.isEmpty()) {
                  libDirPath
                } else {
                  "$libDirPath:$ldLibraryPath"
                }

            // Set TMPDIR
            finalEnv["TMPDIR"] = Environment.TMP_DIR.absolutePath

            log.info("PATH set to: ${finalEnv["PATH"]}")
            log.info("LD_LIBRARY_PATH set to: ${finalEnv["LD_LIBRARY_PATH"]}")

            val process = processBuilder.start()
            currentBuildProcess = process

            val outputReader = process.inputStream.bufferedReader()
            val errorReader = process.errorStream.bufferedReader()

            val outputReaderJob =
                buildServiceScope.launch(Dispatchers.IO) {
                  try {
                    outputReader.useLines { lines -> lines.forEach { line -> logOutput(line) } }
                  } catch (error: Throwable) {
                    if (shouldIgnoreProcessStreamError(error)) {
                      log.debug("Ignoring gradle stdout stream close during cancellation/teardown")
                    } else {
                      log.error("Failed while reading gradle stdout", error)
                    }
                  }
                }

            val errorReaderJob =
                buildServiceScope.launch(Dispatchers.IO) {
                  try {
                    errorReader.useLines { lines -> lines.forEach { line -> logOutput(line) } }
                  } catch (error: Throwable) {
                    if (shouldIgnoreProcessStreamError(error)) {
                      log.debug("Ignoring gradle stderr stream close during cancellation/teardown")
                    } else {
                      log.error("Failed while reading gradle stderr", error)
                    }
                  }
                }

            val exitCode = process.waitFor()

            kotlinx.coroutines.runBlocking {
              outputReaderJob.join()
              errorReaderJob.join()
            }
            currentBuildProcess = null

            val result =
                if (exitCode == 0) {
                  TaskExecutionResult(true, null)
                } else {
                  TaskExecutionResult(false, TaskExecutionResult.Failure.BUILD_FAILED)
                }

            if (result.isSuccessful) {
              onBuildSuccessful(BuildResult(tasksList))
            } else {
              onBuildFailed(BuildResult(tasksList))
            }

            result
          } catch (e: Exception) {
            log.error("Failed to execute gradlew with sh", e)
            val result = TaskExecutionResult(false, TaskExecutionResult.Failure.BUILD_FAILED)
            onBuildFailed(BuildResult(tasksList))
            currentBuildProcess = null
            result
          }
        }
    )
  }

  /** Kills any running gradlew processes forcefully */
  private fun killGradlewProcesses() {
    try {
      log.info("Attempting to kill running gradlew processes...")

      // Use pkill to kill gradlew processes
      val command = listOf("pkill", "-f", "gradlew")
      val processBuilder = ProcessBuilder(command)

      val process = processBuilder.start()
      val exitCode = process.waitFor()

      if (exitCode == 0) {
        log.info("Gradlew processes killed successfully")
        eventListener?.onOutput("All Gradle build processes terminated")
      } else {
        log.info("No gradlew processes found or already terminated")
      }
    } catch (e: Exception) {
      log.error("Error killing gradlew processes", e)
    }
  }

  override fun cancelCurrentBuild(): CompletableFuture<BuildCancellationRequestResult> {
    checkServerStarted()
    if (server == null && bspServer != null) {
      killGradlewProcesses()
      currentBuildProcess?.destroy()
      return CompletableFuture.completedFuture(BuildCancellationRequestResult(true, null))
    }

    val cancellationFuture = server!!.cancelCurrentBuild()

    buildServiceScope.launch {
      try {
        kotlinx.coroutines.delay(1000) // Wait 1 second for graceful cancellation
        log.info("Force stopping Gradle daemons after build cancellation...")
        // stopGradleDaemons().get()
        killGradlewProcesses()
        currentBuildProcess?.destroy()
      } catch (e: Exception) {
        log.error("Error during forced daemon shutdown", e)
      }
    }

    return cancellationFuture
  }

  override fun cleanupIdleResources(trigger: String): CompletableFuture<Boolean> {
    return CompletableFuture.supplyAsync {
      if (isBuildInProgress) {
        log.info("Skip runtime cleanup because a build is in progress. trigger={}", trigger)
        return@supplyAsync false
      }

      try {
        log.info("Running idle runtime cleanup. trigger={}", trigger)
        eventListener?.onOutput("Running runtime cleanup ($trigger)...")

        stopGradleDaemons().get(8, TimeUnit.SECONDS)
        killGradlewProcesses()
        currentBuildProcess?.destroy()
        currentBuildProcess = null

        try {
          server?.shutdown()?.get(2, TimeUnit.SECONDS)
        } catch (e: Throwable) {
          log.warn("Tooling server shutdown during cleanup failed", e)
        }

        toolingServerRunner?.release()
        toolingServerRunner = null
        server = null
        isToolingServerStarted = false

        Runtime.getRuntime().gc()
        System.gc()
        eventListener?.onOutput("Runtime cleanup completed ($trigger)")
        true
      } catch (e: Throwable) {
        log.error("Runtime cleanup failed. trigger={}", trigger, e)
        false
      }
    }
  }

  private fun <T> performBuildTasks(future: CompletableFuture<T>): CompletableFuture<T> {
    val serviceRef = WeakReference(this)
    val taskFuture = CompletableFuture.runAsync { onPrepareBuildRequest() }
        .thenCompose { future }
        .whenComplete { _, _ ->
          serviceRef.get()?.isBuildInProgress = false
        }
    pendingBuildRequests.add(taskFuture)
    taskFuture.whenComplete { _, _ -> pendingBuildRequests.remove(taskFuture) }
    return taskFuture
  }

  private fun onPrepareBuildRequest() {
    checkServerStarted()
    ensureTmpdir()
    if (isBuildInProgress) {
      logBuildInProgress()
      throw BuildInProgressException()
    }
    isBuildInProgress = true
  }

  @Throws(ToolingServerNotStartedException::class)
  private fun checkServerStarted() {
    if (!isToolingServerStarted()) {
      throw ToolingServerNotStartedException()
    }
  }

  private fun ensureTmpdir() {
    Environment.mkdirIfNotExits(Environment.TMP_DIR)
  }

  private fun logBuildInProgress() {
    log.warn("A build is already in progress!")
  }

  internal fun startToolingServer(listener: OnServerStartListener?) {
    if (!ToolsManager.ensureToolingApiReady()) {
      log.error("Tooling API jar is missing or corrupted. Skip starting tooling server.")
      return
    }

    if (toolingServerRunner?.isRunningOrStarting != true) {
      val envs = TermuxShellEnvironment().getEnvironment(this, false)
      toolingServerRunner = ToolingServerRunner(listener, this).also { it.startAsync(envs) }
      return
    }

    if (toolingServerRunner!!.isStarted && toolingServerRunner!!.pid != null && listener != null) {
      listener.onServerStarted(toolingServerRunner!!.pid!!)
    } else {
      setServerListener(listener)
    }
  }

  fun setEventListener(eventListener: EventListener?): GradleBuildService {
    if (eventListener == null) {
      this.eventListener = null
      return this
    }
    this.eventListener = wrap(eventListener)
    return this
  }

  private fun wrap(listener: EventListener?): EventListener? {
    return if (listener == null) {
      null
    } else
        object : EventListener {
          override fun prepareBuild(buildInfo: BuildInfo) {
            runOnUiThread { listener.prepareBuild(buildInfo) }
          }

          override fun onBuildSuccessful(tasks: List<String?>) {
            runOnUiThread { listener.onBuildSuccessful(tasks) }
          }

          override fun onProgressEvent(event: ProgressEvent) {
            runOnUiThread { listener.onProgressEvent(event) }
          }

          override fun onBuildFailed(tasks: List<String?>) {
            runOnUiThread { listener.onBuildFailed(tasks) }
          }

          override fun onOutput(line: String?) {
            runOnUiThread { listener.onOutput(line) }
          }
        }
  }

  private fun startServerOutputReader(input: InputStream) {
    if (outputReaderJob?.isActive == true) {
      return
    }

    outputReaderJob =
        buildServiceScope.launch(Dispatchers.IO + CoroutineName("ToolingServerErrorReader")) {
          val reader = input.bufferedReader()
          try {
            reader.forEachLine { line -> SERVER_System_err.error(line) }
          } catch (e: Throwable) {
            e.ifCancelledOrInterrupted(suppress = true) {
              // will be suppressed
              return@launch
            }
            if (shouldIgnoreProcessStreamError(e)) {
              log.debug("Ignoring tooling server output stream close during cancellation/teardown")
              return@launch
            }

            // log the error and fail silently
            log.error("Failed to read tooling server output", e)
          }
        }
  }

  private fun shouldIgnoreProcessStreamError(error: Throwable): Boolean {
    if (error is InterruptedIOException) {
      return true
    }

    if (error is IOException) {
      val message = error.message?.lowercase().orEmpty()
      if (message.contains("interrupted") || message.contains("closed")) {
        return true
      }
    }

    return !isBuildInProgress
  }

  /** Handles events received from a Gradle build. */
  interface EventListener {

    /**
     * Called just before a build is started.
     *
     * @param buildInfo The information about the build to be executed.
     * @see IToolingApiClient.prepareBuild
     */
    fun prepareBuild(buildInfo: BuildInfo)

    /**
     * Called when a build is successful.
     *
     * @param tasks The tasks that were run.
     * @see IToolingApiClient.onBuildSuccessful
     */
    fun onBuildSuccessful(tasks: List<String?>)

    /**
     * Called when a progress event is received from the Tooling API server.
     *
     * @param event The event model describing the event.
     */
    fun onProgressEvent(event: ProgressEvent)

    /**
     * Called when a build fails.
     *
     * @param tasks The tasks that were run.
     * @see IToolingApiClient.onBuildFailed
     */
    fun onBuildFailed(tasks: List<String?>)

    /**
     * Called when the output line is received.
     *
     * @param line The line of the build output.
     */
    fun onOutput(line: String?)
  }
}
