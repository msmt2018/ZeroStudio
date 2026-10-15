package com.itsaky.androidide.plugins.tasks;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.gradle.api.GradleException;
import org.gradle.api.resources.TextResource;
import org.gradle.util.GradleVersion;

class GradleVersionResolver {

  private static final String LATEST = "latest";
  private static final String NIGHTLY = "nightly";
  private static final String RELEASE_CANDIDATE = "release-candidate";
  private static final String RELEASE_NIGHTLY = "release-nightly";
  
  private static final List<String> PLACE_HOLDERS = Arrays.asList(
      LATEST, NIGHTLY, RELEASE_CANDIDATE, RELEASE_NIGHTLY, "current"
  );

  private TextResource latest;
  private TextResource releaseCandidate;
  private TextResource nightly;
  private TextResource releaseNightly;

  private GradleVersion gradleVersion;
  private String gradleVersionString = GradleVersion.current().getVersion();

  void setTextResources(TextResource latest, TextResource releaseCandidate, TextResource nightly,
      TextResource releaseNightly) {
    this.latest = latest;
    this.releaseCandidate = releaseCandidate;
    this.nightly = nightly;
    this.releaseNightly = releaseNightly;
  }

  String resolve(String version) {
    if (version == null) {
      return GradleVersion.current().getVersion();
    }
    switch (version) {
      case LATEST:
        return getVersion(latest.asString(), version);
      case NIGHTLY:
        return getVersion(nightly.asString(), version);
      case RELEASE_NIGHTLY:
        return getVersion(releaseNightly.asString(), version);
      case RELEASE_CANDIDATE:
        return getVersion(releaseCandidate.asString(), version);
      default:
        return version;
    }
  }

  static String getVersion(String json, String placeHolder) {
    Type type = new TypeToken<Map<String, String>>() {
    }.getType();
    Map<String, String> map = new Gson().fromJson(json, type);
    String version = map.get("version");
    if (version == null) {
      throw new GradleException(
          "There is currently no version information available for '" + placeHolder + "'.");
    }
    return version;
  }

  static boolean isPlaceHolder(String version) {
    return PLACE_HOLDERS.contains(version);
  }

  GradleVersion getGradleVersion() {
    if (gradleVersion == null) {
      gradleVersion = GradleVersion.version(resolve(gradleVersionString));
    }
    return gradleVersion;
  }

  void setGradleVersionString(String gradleVersionString) {
    if (!isPlaceHolder(gradleVersionString)) {
      this.gradleVersion = GradleVersion.version(gradleVersionString);
    }
    if (this.gradleVersionString != gradleVersionString) {
      this.gradleVersionString = gradleVersionString;
      this.gradleVersion = null;
    }
  }
}
