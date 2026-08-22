/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package com.itsaky.androidide.app

import android.app.Application
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Exposes the concrete application type to Hilt consumers without activity-side casts. */
@Module
@InstallIn(SingletonComponent::class)
object IDEApplicationModule {

  @Provides
  fun provideIDEApplication(application: Application): IDEApplication = application as IDEApplication
}
