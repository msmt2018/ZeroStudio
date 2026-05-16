package com.itsaky.androidide.tooling.api.bsp

import ch.epfl.scala.bsp4j.BuildServer
import ch.epfl.scala.bsp4j.CompileParams
import ch.epfl.scala.bsp4j.CompileResult
import ch.epfl.scala.bsp4j.ExitBuildParams
import ch.epfl.scala.bsp4j.InitializeBuildResult
import ch.epfl.scala.bsp4j.OnBuildInitializedParams
import ch.epfl.scala.bsp4j.TaskId
import ch.epfl.scala.bsp4j.WorkspaceBuildTargetsParams
import ch.epfl.scala.bsp4j.WorkspaceBuildTargetsResult
import ch.epfl.scala.bsp4j.SourcesParams
import ch.epfl.scala.bsp4j.SourcesResult
import ch.epfl.scala.bsp4j.DependencySourcesParams
import ch.epfl.scala.bsp4j.DependencySourcesResult
import ch.epfl.scala.bsp4j.DependencyModulesParams
import ch.epfl.scala.bsp4j.DependencyModulesResult
import java.util.concurrent.CompletableFuture

/**
 * BSP-first service contract used by higher modules to avoid direct dependency on legacy RPC
 * contracts.
 */
interface BspBuildService {
  fun initialize(rootUri: String): CompletableFuture<InitializeBuildResult>

  fun compile(params: CompileParams): CompletableFuture<CompileResult>

  fun workspaceBuildTargets(params: WorkspaceBuildTargetsParams): CompletableFuture<WorkspaceBuildTargetsResult>
  fun buildTargetSources(params: SourcesParams): CompletableFuture<SourcesResult>
  fun buildTargetDependencySources(params: DependencySourcesParams): CompletableFuture<DependencySourcesResult>
  fun buildTargetDependencyModules(params: DependencyModulesParams): CompletableFuture<DependencyModulesResult>

  fun cancel(taskId: TaskId): CompletableFuture<Any>

  fun shutdown(): CompletableFuture<Any>
}

class DefaultBspBuildService(private val connection: BspServerConnection) : BspBuildService {

  private val server: BuildServer
    get() = connection.server

  override fun initialize(rootUri: String): CompletableFuture<InitializeBuildResult> {
    return connection.initialize(rootUri).thenApply {
      server.onBuildInitialized(OnBuildInitializedParams())
      it
    }
  }

  override fun compile(params: CompileParams): CompletableFuture<CompileResult> =
    server.buildTargetCompile(params)

  override fun workspaceBuildTargets(
    params: WorkspaceBuildTargetsParams
  ): CompletableFuture<WorkspaceBuildTargetsResult> = server.workspaceBuildTargets(params)

  override fun buildTargetSources(params: SourcesParams): CompletableFuture<SourcesResult> =
    server.buildTargetSources(params)

  override fun buildTargetDependencySources(
    params: DependencySourcesParams
  ): CompletableFuture<DependencySourcesResult> = server.buildTargetDependencySources(params)

  override fun buildTargetDependencyModules(
    params: DependencyModulesParams
  ): CompletableFuture<DependencyModulesResult> = server.buildTargetDependencyModules(params)

  override fun cancel(taskId: TaskId): CompletableFuture<Any> = server.buildCancel(taskId)

  override fun shutdown(): CompletableFuture<Any> {
    return connection.shutdown().thenApply {
      server.onBuildExit(ExitBuildParams())
      it
    }
  }
}
