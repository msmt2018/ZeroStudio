package com.itsaky.androidide.bsp.ipc;

import com.itsaky.androidide.bsp.ipc.IBspSessionCallback;

interface IBspSessionService {
  void registerCallback(IBspSessionCallback callback);
  void unregisterCallback(IBspSessionCallback callback);
  String initialize(String rootUri, String optionsJson);
  String syncWorkspace(String optionsJson);
  String compile(String targetIdsJson, String optionsJson);
  String test(String targetIdsJson, String optionsJson);
  String cancel(String taskId);
  String shutdown(String optionsJson);
}
