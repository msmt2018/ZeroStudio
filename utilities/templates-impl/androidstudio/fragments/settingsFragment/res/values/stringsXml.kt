/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.itsaky.androidide.templates.impl.androidstudio.fragments.settingsFragment.res.values

fun stringsXml() =
  """
<resources>
    <!-- Preference Titles -->
    <string name="messages_header">Messages</string>
    <string name="sync_header">Sync</string>

    <!-- Messages Preferences -->
    <string name="signature_title">Your signature</string>
    <string name="reply_title">Default reply action</string>

    <!-- Sync Preferences -->
    <string name="sync_title">Sync email periodically</string>
    <string name="attachment_title">Download incoming attachments</string>
    <string name="attachment_summary_on">Automatically download attachments for incoming emails</string>
    <string name="attachment_summary_off">Only download attachments when manually requested</string>
</resources>
"""
