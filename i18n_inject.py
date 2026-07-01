#!/usr/bin/env python3
"""
Step 1: Insert English-default entries for all the new keys into every
non-default values-*/strings.xml (zh-rCN already done). This way every
locale has the strings; the actual translation is done in step 2.
"""
import re
import os
import sys

LOCALES = [
    "ar-rSA", "bn-rIN", "de-rDE", "es-rES", "fa", "fil", "fr-rFR",
    "hi-rIN", "in-rID", "it", "ja", "ko", "ml", "pl", "pt-rBR",
    "ro-rRO", "ru-rRU", "ta", "th", "tm-rTM", "tr-rTR", "uk", "vi",
    "zh-rTW",
]

RES_DIR = "/workspace/core/resources/src/main/res"

# Default English values (must match the ones in values/strings.xml).
EN = {
    "swipe_action_pin": "Pin",
    "swipe_action_unpin": "Unpin",
    "swipe_action_delete": "Delete",
    "swipe_action_open_hint": "Open",
    "delete_warning_disk": "The project files on disk will be permanently removed. This cannot be undone.",
    "delete_in_progress": "Deleting project\u2026",
    "delete_project_failed": "Delete failed",
    "delete_in_progress_hint": "Removing project directory and clearing history entry.",
    "delete_failed_hint": "The project entry will still be cleared from history; you can retry the disk delete later.",
    "delete_success": "Project deleted",
    "delete_incomplete": "Delete incomplete",
    "delete_success_detail": "\u201c%1$s\u201d has been removed from the recent list and deleted from disk.",
    "delete_incomplete_detail": "Some files could not be removed. Please close the project in the editor first, then try again.",
    "action_close": "Close",
    "ok": "OK",
    "od_sdk_step_environment": "STEP  \u2022  ENVIRONMENT",
    "od_sdk_title": "SDK Installation &amp; Configuration",
    "od_sdk_subtitle": "Install development tools for the IDE to ensure proper operation",
    "od_sdk_abi_badge": "ABI  %1$s",
    "od_sdk_network_ok": "Network connected",
    "od_sdk_loading": "Fetching SDK list\u2026",
    "od_sdk_empty": "No components available",
    "od_sdk_required": "REQUIRED",
    "od_sdk_selected_count": "%1$d selected",
    "od_sdk_selected_of_total": "%1$d / %2$d",
    "od_sdk_additional_configs": "Additional Configurations",
    "od_sdk_install_git": "Install Git",
    "od_sdk_install_ssh": "Install SSH",
    "od_sdk_fix_ndk": "Fix NDK",
    "od_sdk_fix_cmake": "Fix CMake",
    "od_sdk_github_mirror": "GitHub Mirror",
    "od_sdk_offline_install": "Offline Install",
    "od_sdk_reload": "Reload",
    "od_sdk_mirror_placeholder": "https://gh.llkk.cc/",
    "od_sdk_jdk_label": "JDK %1$s",
    "od_sdk_openjdk_17_recommended": "OpenJDK 17 (Recommended)",
    "od_sdk_openjdk_21_experimental": "OpenJDK 21 (Experimental)",
    "od_sdk_start_offline": "Start Offline Installation",
    "od_sdk_start_setup": "Start Environment Setup",
    "od_sdk_confirm_installation": "Confirm Installation",
    "od_sdk_setup_completed": "Setup Completed",
    "od_sdk_components_to_install": "Components to install/update:",
    "od_sdk_openjdk_version": "- OpenJDK %1$s",
    "od_sdk_git_version_control": "- Git Version Control",
    "od_sdk_openssh_remote_auth": "- OpenSSH Remote Auth",
    "od_sdk_additional_configurations": "Additional Configurations:",
    "od_sdk_apply_ndk_fixes": "\u2022 Apply NDK Fixes (symlinks &amp; patches)",
    "od_sdk_apply_cmake_patches": "\u2022 Apply CMake Patches",
    "od_sdk_active_github_mirror": "\u2022 Active Github Mirror: %1$s",
    "od_sdk_current_task": "Current: %1$s",
    "od_sdk_execute": "Execute",
    "od_sdk_finish_launch": "Finish &amp; Launch",
    "od_sdk_offline_installation": "Offline Installation",
    "od_sdk_offline_setup_completed": "Offline Setup Completed",
    "od_sdk_select_offline_package": "Please select the offline resources package (sdkresources.tar.gz):",
    "od_sdk_content_placeholder": "content://\u2026",
    "od_sdk_select": "Select",
    "od_sdk_log_configuring_package": "Configuring package environment\u2026",
    "od_sdk_log_updating_pkg": "\u00bb Updating pkg repositories\u2026",
    "od_sdk_log_installing_base": "\u00bb Installing required base packages\u2026",
    "od_sdk_log_checking_tools": "Checking extraction tools\u2026",
    "od_sdk_log_verifying_tools": "\u00bb Verifying unzip/7z/tar availability\u2026",
    "od_sdk_log_warn_err_tools": "WARN/ERR tools check: %1$s",
    "od_sdk_log_installing_git": "\u00bb Installing Git\u2026",
    "od_sdk_log_installing_openssh": "\u00bb Installing OpenSSH\u2026",
    "od_sdk_log_installing_openjdk_task": "Installing OpenJDK %1$s\u2026",
    "od_sdk_log_installing_pkg_openjdk": "\u00bb Installing package: \\'openjdk-%1$s\\'",
    "od_sdk_log_jdk_installed": "\u00bb JDK %1$s has been installed.",
    "od_sdk_log_updating_properties": "\u00bb Updating ide-environment.properties\u2026",
    "od_sdk_log_java_home": "\u00bb JAVA_HOME=%1$s",
    "od_sdk_log_properties_updated": "\u00bb Properties file updated successfully!",
    "od_sdk_log_properties_failed": "WARN: Failed to write ide-environment.properties: %1$s",
    "od_sdk_log_installing_node": "Installing %1$s",
    "od_sdk_log_install_node_failed": "ERROR: Failed to install %1$s. Continuing next task.",
    "od_sdk_log_all_tasks_completed": "All tasks completed. Environment is ready!",
    "od_sdk_log_no_file_selected": "ERR: No file selected!",
    "od_sdk_log_preparing_offline": "Preparing offline package\u2026",
    "od_sdk_log_copying_file": "\u00bb Copying selected file to HOME\u2026",
    "od_sdk_log_file_copied": "\u00bb File copied. Installing tar &amp; dpkg\u2026",
    "od_sdk_log_executing_offline": "Executing offline installation\u2026",
    "od_sdk_log_error_with_msg": "ERR: %1$s",
}

# Quoting helper: wrap value in double quotes, escape backslashes and
# double-quotes. Apostrophes and angle brackets are kept verbatim.
#
# IMPORTANT: Android string resources require an apostrophe (') to be either
# escaped as \' OR the entire string wrapped in double quotes.  Without this,
# AAPT2 will treat the apostrophe as the start of a literal string and emit
# a confusing "Invalid unicode escape sequence in string" error pointing at
# unrelated surrounding strings (e.g. values-uk/strings.xml od_sdk_required
# was failing because ОБОВ'ЯЗКОВО had an unescaped apostrophe).
def xml_escape(s: str) -> str:
    s = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    # If the value contains an unescaped apostrophe, wrap the whole value in
    # double quotes so AAPT2 treats the apostrophe as a literal character.
    # Backslashes and double quotes inside the value are also escaped so the
    # outer double-quote wrapping stays valid.
    if "'" in s:
        s = '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'
    return s

# Build the XML block to insert before </resources>.
def build_block(mapping):
    lines = []
    lines.append("")
    lines.append("  <!-- Project list swipe action menu / delete flow -->")
    for k in [
        "swipe_action_pin", "swipe_action_unpin", "swipe_action_delete",
        "swipe_action_open_hint", "delete_warning_disk",
        "delete_in_progress", "delete_project_failed",
        "delete_in_progress_hint", "delete_failed_hint",
        "delete_success", "delete_incomplete",
        "delete_success_detail", "delete_incomplete_detail",
        "action_close", "ok",
    ]:
        lines.append(f'  <string name="{k}">{xml_escape(mapping[k])}</string>')
    lines.append("")
    lines.append("  <!-- SDK installation / configuration onboarding -->")
    for k in [
        "od_sdk_step_environment", "od_sdk_title", "od_sdk_subtitle",
        "od_sdk_abi_badge", "od_sdk_network_ok", "od_sdk_loading",
        "od_sdk_empty", "od_sdk_required", "od_sdk_selected_count",
        "od_sdk_selected_of_total", "od_sdk_additional_configs",
        "od_sdk_install_git", "od_sdk_install_ssh", "od_sdk_fix_ndk",
        "od_sdk_fix_cmake", "od_sdk_github_mirror", "od_sdk_offline_install",
        "od_sdk_reload", "od_sdk_mirror_placeholder", "od_sdk_jdk_label",
        "od_sdk_openjdk_17_recommended", "od_sdk_openjdk_21_experimental",
        "od_sdk_start_offline", "od_sdk_start_setup",
        "od_sdk_confirm_installation", "od_sdk_setup_completed",
        "od_sdk_components_to_install", "od_sdk_openjdk_version",
        "od_sdk_git_version_control", "od_sdk_openssh_remote_auth",
        "od_sdk_additional_configurations", "od_sdk_apply_ndk_fixes",
        "od_sdk_apply_cmake_patches", "od_sdk_active_github_mirror",
        "od_sdk_current_task", "od_sdk_execute", "od_sdk_finish_launch",
        "od_sdk_offline_installation", "od_sdk_offline_setup_completed",
        "od_sdk_select_offline_package", "od_sdk_content_placeholder",
        "od_sdk_select", "od_sdk_log_configuring_package",
        "od_sdk_log_updating_pkg", "od_sdk_log_installing_base",
        "od_sdk_log_checking_tools", "od_sdk_log_verifying_tools",
        "od_sdk_log_warn_err_tools", "od_sdk_log_installing_git",
        "od_sdk_log_installing_openssh", "od_sdk_log_installing_openjdk_task",
        "od_sdk_log_installing_pkg_openjdk", "od_sdk_log_jdk_installed",
        "od_sdk_log_updating_properties", "od_sdk_log_java_home",
        "od_sdk_log_properties_updated", "od_sdk_log_properties_failed",
        "od_sdk_log_installing_node", "od_sdk_log_install_node_failed",
        "od_sdk_log_all_tasks_completed", "od_sdk_log_no_file_selected",
        "od_sdk_log_preparing_offline", "od_sdk_log_copying_file",
        "od_sdk_log_file_copied", "od_sdk_log_executing_offline",
        "od_sdk_log_error_with_msg",
    ]:
        lines.append(f'  <string name="{k}">{xml_escape(mapping[k])}</string>')
    return "\n".join(lines)

def main():
    only_locale = sys.argv[1] if len(sys.argv) > 1 else None
    for loc in LOCALES:
        if only_locale and loc != only_locale:
            continue
        path = os.path.join(RES_DIR, f"values-{loc}", "strings.xml")
        with open(path, "r", encoding="utf-8") as f:
            content = f.read()
        # If the file already contains od_sdk_step_environment, skip.
        if "od_sdk_step_environment" in content:
            print(f"skip {loc} (already has new keys)")
            continue
        block = build_block(EN)
        # Insert block immediately before </resources>
        new_content = content.replace("</resources>", block + "\n</resources>")
        with open(path, "w", encoding="utf-8") as f:
            f.write(new_content)
        print(f"updated {loc}")

if __name__ == "__main__":
    main()
