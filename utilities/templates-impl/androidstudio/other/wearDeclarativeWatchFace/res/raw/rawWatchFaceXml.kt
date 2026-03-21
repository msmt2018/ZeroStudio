/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.itsaky.androidide.templates.impl.androidstudio.other.wearDeclarativeWatchFace.res.raw

fun rawWatchFaceXml() =
  // language=XML
  """
<WatchFace width="450" height="450">
  <Metadata key="CLOCK_TYPE" value="DIGITAL"/>
    <Scene>
    <DigitalClock x="0" y="0" width="450" height="450">
      <TimeText format="hh:mm" x="0" y="175" width="450" height="100">
        <Variant mode="AMBIENT" target="alpha" value="0"/>
        <Font color="#ffffffff" family="SYNC_TO_DEVICE" size="128" />
      </TimeText>
      <TimeText alpha="0" format="hh:mm" x="0" y="175" width="450" height="100">
        <Variant mode="AMBIENT" target="alpha" value="255"/>
        <Font color="#ffffffff" family="SYNC_TO_DEVICE" size="128" weight="THIN" />
      </TimeText>
    </DigitalClock>
    <Group x="0" y="0" width="450" height="450" name="hello_world">
      <PartText x="0" y="285" width="450" height="50">
        <Variant mode="AMBIENT" target="alpha" value="0"/>
        <Text>
          <Font color="#ffffffff" family="SYNC_TO_DEVICE" size="36">
            <Template>%s<Parameter expression="greeting"/></Template>
          </Font>
        </Text>
      </PartText>
    </Group>
  </Scene>
</WatchFace>
"""
