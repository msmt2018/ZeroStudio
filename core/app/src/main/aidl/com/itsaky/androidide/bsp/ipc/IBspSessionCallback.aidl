package com.itsaky.androidide.bsp.ipc;

interface IBspSessionCallback {
  void onEvent(String topic, String payloadJson);
}
