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

/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.itsaky.androidide.plugins.tasks;

import java.util.Set;
import org.gradle.api.GradleException;
import org.gradle.util.GradleVersion;

/**
 * Resolves concrete Gradle versions for the IDE wrapper generator.
 *
 * Gradle 9.6 no longer exposes the internal wrapper version-resource
 * implementation used by older AndroidIDE sources. Keep version handling
 * on the public GradleVersion API and require concrete versions for aliases.
 */
class GradleVersionResolver {

  private static final Set<String> PLACEHOLDERS =
      Set.of("latest", "nightly", "release-nightly", "release-candidate");

  private GradleVersion gradleVersion;
  private String gradleVersionString = GradleVersion.current().getVersion();

  String resolve(String version) {
    if (version == null) {
      return GradleVersion.current().getVersion();
    }
    if (isPlaceHolder(version)) {
      throw new GradleException(
          "Gradle version alias '" + version
              + "' cannot be resolved by this wrapper generator. Specify an explicit Gradle version.");
    }
    return version;
  }

  static boolean isPlaceHolder(String version) {
    return PLACEHOLDERS.contains(version);
  }

  GradleVersion getGradleVersion() {
    if (gradleVersion == null) {
      gradleVersion = GradleVersion.version(resolve(gradleVersionString));
    }
    return gradleVersion;
  }

  void setGradleVersionString(String gradleVersionString) {
    if (!this.gradleVersionString.equals(gradleVersionString)) {
      this.gradleVersionString = gradleVersionString;
      this.gradleVersion = null;
    }
  }
}
