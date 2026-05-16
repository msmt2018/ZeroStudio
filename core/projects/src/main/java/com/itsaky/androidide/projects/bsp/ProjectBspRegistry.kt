package com.itsaky.androidide.projects.bsp

import com.itsaky.androidide.lookup.Lookup
import com.itsaky.androidide.lookup.Lookup.Key
import com.itsaky.androidide.tooling.api.bsp.BspBuildService

/** Registry entry point for BSP services at the projects layer. */
object ProjectBspRegistry {
  @JvmField val KEY_BSP_BUILD_SERVICE = Key<BspBuildService>()

  fun register(service: BspBuildService) {
    Lookup.getDefault().update(KEY_BSP_BUILD_SERVICE, service)
  }

  fun unregister() {
    Lookup.getDefault().unregister(KEY_BSP_BUILD_SERVICE)
  }

  fun lookup(): BspBuildService? = Lookup.getDefault().lookup(KEY_BSP_BUILD_SERVICE)
}
