/*
 * UniversalPreviewEngineFragment — 一站式 3D/2D 图形空间渲染预览组件
 *
 * 终极双核架构:
 *   核心A (扩展性核心): WebView + Three.js / WebAssembly
 *     负责: 静态 AST 解析、代码拓扑图、数据可视化
 *   核心B (性能核心):   GLSurfaceView (EGL 3.0) + JNI C++ NDK
 *     负责: 高帧率 3D 游戏模型渲染、物理算法、Dear ImGui 实时交互
 *
 * 调度器 switchEngineMode(int mode):
 *   mode = 0 → 激活 Web 核, 冻结 NDK 渲染线程
 *   mode = 1 → 激活 NDK 核, 暂停 Web 核
 *
 * 统一数据中台 dispatchSourceData(int type, String payload):
 *   type = 0 → payload 作为 JSON 投递给 WebView (Three.js 空间重绘)
 *   type = 1 → payload 投递给 native nativeUpdateScene (C++ / ImGui 渲染)
 *
 * 极端场景处理:
 *   - 屏幕旋转: Fragment 会被销毁重建, onDestroyView 释放全部资源,
 *     新实例 onCreateView 重新初始化。WebView 不能跨 Fragment 复用
 *     (会泄漏 Activity), 所以每次都新建。GLSurfaceView 的 EGL 上下文
 *     在 onPause 后由 GLSurfaceView 自行管理, onDestroyView 额外调
 *     nativeRelease 兜底。
 *   - 快速切换标签页: switchEngineMode 用 INVISIBLE 而非 GONE,
 *     避免 WebView 反复 layout 抖动; GLSurfaceView onPause 冻结线程。
 *   - 后台返回: onPause 冻结, onResume 恢复, 不丢失场景 (C++ 侧
 *     保留场景对象, Java 侧 WebView 保留 DOM 状态)。
 */

package com.zerostudio.preview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;

/**
 * 混合双核渲染预览 Fragment。
 *
 * <p>用法 (在宿主 Activity 里):
 * <pre>
 *   getSupportFragmentManager().beginTransaction()
 *       .replace(R.id.container, new UniversalPreviewEngineFragment())
 *       .commit();
 *   // 切换到 NDK 高性能核
 *   fragment.switchEngineMode(1);
 *   // 投递 AST JSON 给 Web 核
 *   fragment.dispatchSourceData(0, astJson);
 * </pre>
 */
public class UniversalPreviewEngineFragment extends Fragment {

  private static final String TAG = "UniversalPreview";

  // ── 引擎模式 ──────────────────────────────────────────────
  /** 核心A: WebView + Three.js (静态 / AST / 拓扑) */
  public static final int MODE_WEB = 0;
  /** 核心B: GLSurfaceView + NDK C++ (动态 / 3D 模型 / ImGui) */
  public static final int MODE_NDK = 1;

  // ── 数据类型 ──────────────────────────────────────────────
  /** dispatchSourceData type=0: 静态 / AST / 拓扑 JSON → WebView */
  public static final int DATA_STATIC_AST = 0;
  /** dispatchSourceData type=1: 动态 / C++ 原生渲染 / ImGui → NDK */
  public static final int DATA_NATIVE_SCENE = 1;

  // ── 视图 ──────────────────────────────────────────────────
  private FrameLayout rootContainer;
  private WebView coreWebView;
  private GLSurfaceViewGles3 coreGlSurface;

  /** 当前引擎模式, 默认 Web 核 */
  private int currentMode = MODE_WEB;

  /** native 库是否已加载成功 */
  private static volatile boolean nativeLibLoaded = false;

  // ═══════════════════════════════════════════════════════════
  //  生命周期
  // ═══════════════════════════════════════════════════════════

  @Override
  @NonNull
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {

    // ── 动态构建 FrameLayout 根布局 ──
    // 不用 XML 布局, 避免模块间资源依赖; 全部代码构建。
    rootContainer = new FrameLayout(requireContext());
    rootContainer.setLayoutParams(
        new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));

    // ── 初始化核心A: WebView ──
    initWebView();

    // ── 初始化核心B: GLSurfaceView (GLES 3.0) ──
    initGlSurfaceView();

    // 默认 Web 核可见, NDK 核隐藏
    applyMode(currentMode);

    return rootContainer;
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    // ── 加载本地 C++ 引擎 ──
    // 在 onViewCreated 而非 onCreate 加载: 确保 GLSurfaceView 已创建,
    // nativeInit 需要在 EGL 上下文就绪后调 (由 GLSurfaceView.Renderer
    // 的 onSurfaceCreated 回调触发, 不在这里直接调)。
    loadNativeEngine();
  }

  @Override
  public void onResume() {
    super.onResume();
    // 激活 3D 渲染循环 (GLSurfaceView 内部 GLThread 恢复)
    if (coreGlSurface != null) {
      coreGlSurface.onResume();
    }
    // WebView 恢复 (恢复 JS 定时器 / 动画)
    if (coreWebView != null) {
      coreWebView.onResume();
    }
    Log.d(TAG, "onResume: 渲染循环已激活");
  }

  @Override
  public void onPause() {
    super.onPause();
    // 冻结后台渲染线程, 防止手机在后台发热和耗电
    if (coreGlSurface != null) {
      coreGlSurface.onPause();
    }
    if (coreWebView != null) {
      coreWebView.onPause();
    }
    Log.d(TAG, "onPause: 渲染循环已冻结");
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    // ──【核心重点】彻底销毁, 零泄漏 ──

    // 1. 释放 native C++ 资源 (ImGui 上下文 / GL buffer / 纹理)
    //    必须在 GLSurfaceView 销毁前调, 因为 nativeRelease 可能还在
    //    GL 线程上执行最后一次 cleanup。
    if (nativeLibLoaded) {
      try {
        nativeRelease();
      } catch (UnsatisfiedLinkError e) {
        Log.w(TAG, "nativeRelease 失败 (库可能已卸载)", e);
      }
    }

    // 2. 销毁 GLSurfaceView: 从父布局移除
    //    (不调 setRenderer(null) — GLSurfaceView 不允许 null;
    //     onPause 已冻结渲染线程, onDetachedFromWindow 会释放 EGL 上下文)
    if (coreGlSurface != null) {
      coreGlSurface.queueEvent(() -> {
        // EGL 上下文由 GLSurfaceView 内部在 onDetachedFromWindow 释放,
        // 这里只做兜底 log
        Log.d(TAG, "GL 线程即将退出");
      });
      removeFromParent(coreGlSurface);
      coreGlSurface = null;
    }

    // 3. 彻底销毁 WebView (Android WebView 极易泄漏 Activity Context)
    if (coreWebView != null) {
      // 先停止加载 + 移除 JS 接口, 防止回调持有 Fragment
      coreWebView.stopLoading();
      coreWebView.removeJavascriptInterface("AndroidIDE");
      // 清空缓存 + 历史
      coreWebView.clearCache(true);
      coreWebView.clearHistory();
      // 从父布局移除 (关键: 不移除的话 WebView 会持有 parent 引用链)
      removeFromParent(coreWebView);
      // 最后一刀: destroy() 释放内部 WebViewProvider
      coreWebView.destroy();
      coreWebView = null;
    }

    rootContainer = null;
    Log.d(TAG, "onDestroyView: 全部资源已回收");
  }

  // ═══════════════════════════════════════════════════════════
  //  核心A: WebView 初始化
  // ═══════════════════════════════════════════════════════════

  @SuppressLint("SetJavaScriptEnabled")
  private void initWebView() {
    coreWebView = new WebView(requireContext());

    // 硬件加速层 (Fragment 已默认硬件加速, 但 WebView 需显式确认)
    coreWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

    WebSettings settings = coreWebView.getSettings();
    // 启用 JavaScript (Three.js / WASM 必需)
    settings.setJavaScriptEnabled(true);
    // 启用 WebGL2 (Android 9+ Chrome 内核原生支持; 旧版回退 WebGL1)
    settings.setMediaPlaybackRequiresUserGesture(false);
    // DOM Storage (Three.js 模型缓存 / IndexedDB)
    settings.setDomStorageEnabled(true);
    settings.setDatabaseEnabled(true);
    // 允许本地文件访问 (assets:// 协议加载 universal_viewer.html)
    settings.setAllowFileAccess(true);
    settings.setAllowContentAccess(true);
    // 缩放关闭 (3D 场景自己处理手势)
    settings.setBuiltInZoomControls(false);
    settings.setDisplayZoomControls(false);
    settings.setSupportZoom(false);
    // 混合内容允许 (Three.js CDN fallback 时需要)
    settings.setMixedContentMode(
        android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

    // 注入 @JavascriptInterface 桥接对象 "AndroidIDE"
    // JS 端可通过 AndroidIDE.onSceneReady() / AndroidIDE.log(msg) 回调 Java
    coreWebView.addJavascriptInterface(new IdeBridge(), "AndroidIDE");

    // WebChromeClient: 让 console.log 重定向到 Logcat
    coreWebView.setWebChromeClient(
        new WebChromeClient() {
          @Override
          public void onConsoleMessage(
              android.webkit.ConsoleMessage consoleMessage) {
            Log.d(
                "UniversalPreview/JS",
                consoleMessage.message() + " (" + consoleMessage.sourceId()
                    + ":" + consoleMessage.lineNumber() + ")");
          }
        });

    // 加载本地 assets 中的 universal_viewer.html
    coreWebView.loadUrl("file:///android_asset/universal_viewer.html");

    FrameLayout.LayoutParams webParams =
        new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT);
    rootContainer.addView(coreWebView, webParams);
  }

  // ═══════════════════════════════════════════════════════════
  //  核心B: GLSurfaceView (GLES 3.0) 初始化
  // ═══════════════════════════════════════════════════════════

  private void initGlSurfaceView() {
    coreGlSurface = new GLSurfaceViewGles3(requireContext());

    // 透明像素格式 (让 ImGui 半透明 UI 叠加在 3D 场景上)
    coreGlSurface.getHolder().setFormat(PixelFormat.TRANSLUCENT);
    coreGlSurface.setZOrderOnTop(true);

    // 设置 EGL 3.0 上下文工厂
    coreGlSurface.setEGLContextFactory(new Gles3ContextFactory());

    // 设置 Renderer: 调用 native 渲染方法
    // GLSurfaceView.Renderer 3 个回调分别映射到 nativeInit / nativeOnResize / nativeOnDrawFrame
    coreGlSurface.setRenderer(
        new android.opengl.GLSurfaceView.Renderer() {
          @Override
          public void onSurfaceCreated(
              javax.microedition.khronos.opengles.GL10 gl,
              javax.microedition.khronos.egl.EGLConfig config) {
            // native 初始化 (ImGui + shader)
            nativeInit();
          }

          @Override
          public void onSurfaceChanged(
              javax.microedition.khronos.opengles.GL10 gl,
              int width, int height) {
            // viewport resize
            nativeOnResize(width, height);
          }

          @Override
          public void onDrawFrame(
              javax.microedition.khronos.opengles.GL10 gl) {
            // 每帧渲染
            nativeOnDrawFrame();
          }
        });

    // RENDERMODE_CONTINUOUSLY: 持续渲染 (60fps), 适合 3D 游戏 / ImGui
    // onPause 会自动停帧, 不会空转耗电
    coreGlSurface.setRenderMode(
        android.opengl.GLSurfaceView.RENDERMODE_CONTINUOUSLY);

    FrameLayout.LayoutParams glParams =
        new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT);
    rootContainer.addView(coreGlSurface, glParams);
  }

  // ═══════════════════════════════════════════════════════════
  //  调度器: switchEngineMode
  // ═══════════════════════════════════════════════════════════

  /**
   * 切换引擎核心。
   *
   * @param mode {@link #MODE_WEB} (0) = Web 核 / {@link #MODE_NDK} (1) = NDK 核
   */
  public void switchEngineMode(int mode) {
    if (mode != MODE_WEB && mode != MODE_NDK) {
      Log.w(TAG, "switchEngineMode: 未知模式 " + mode + ", 忽略");
      return;
    }
    if (mode == currentMode) {
      return; // 无变化
    }
    currentMode = mode;
    if (rootContainer != null) {
      applyMode(mode);
    }
    Log.d(TAG, "switchEngineMode → " + (mode == MODE_WEB ? "WEB" : "NDK"));
  }

  private void applyMode(int mode) {
    if (coreWebView == null || coreGlSurface == null) return;

    if (mode == MODE_WEB) {
      // Web 核可见, NDK 核隐藏 (用 INVISIBLE 而非 GONE, 避免 GLSurfaceView
      // 反复 onMeasure / onLayout 抖动导致 EGL 上下文丢失)
      coreWebView.setVisibility(View.VISIBLE);
      coreGlSurface.setVisibility(View.INVISIBLE);
      // NDK 核暂停渲染 (省 GPU)
      coreGlSurface.onPause();
    } else {
      coreWebView.setVisibility(View.INVISIBLE);
      coreGlSurface.setVisibility(View.VISIBLE);
      // NDK 核恢复渲染
      coreGlSurface.onResume();
      // Web 核暂停 JS 定时器 (省 CPU)
      coreWebView.onPause();
    }
  }

  // ═══════════════════════════════════════════════════════════
  //  统一数据中台: dispatchSourceData
  // ═══════════════════════════════════════════════════════════

  /**
   * 统一数据入口。
   *
   * @param type    {@link #DATA_STATIC_AST} (0) = JSON → WebView,
   *                {@link #DATA_NATIVE_SCENE} (1) = 源码/库路径 → NDK
   * @param payload 数据内容
   */
  public void dispatchSourceData(int type, @Nullable String payload) {
    if (payload == null) payload = "";

    if (type == DATA_STATIC_AST) {
      // ── type=0: 投递 JSON 给 WebView, 触发 Three.js 空间重绘 ──
      if (coreWebView == null) {
        Log.w(TAG, "dispatchSourceData: WebView 未初始化, 丢弃");
        return;
      }
      // 转义 JSON 中的特殊字符, 防止 JS 注入 / 语法错误
      String escaped = payload.replace("\\", "\\\\").replace("'", "\\'");
      String js = "window.__universalViewer && window.__universalViewer.updateScene('"
          + escaped + "');";
      // post 到 UI 线程执行 (evaluateJavascript 必须在主线程)
      coreWebView.post(
          () -> {
            if (coreWebView != null) {
              coreWebView.evaluateJavascript(js, null);
            }
          });

    } else if (type == DATA_NATIVE_SCENE) {
      // ── type=1: 投递给 native C++ 引擎 ──
      if (!nativeLibLoaded) {
        Log.w(TAG, "dispatchSourceData: native 库未加载, 丢弃");
        return;
      }
      // nativeUpdateScene 内部会 copy payload 到 std::string,
      // 然后解析为场景命令 (加载模型 / 更新 ImGui 面板等)
      try {
        nativeUpdateScene(payload);
      } catch (UnsatisfiedLinkError e) {
        Log.e(TAG, "nativeUpdateScene 调用失败", e);
      }

    } else {
      Log.w(TAG, "dispatchSourceData: 未知 type=" + type);
    }
  }

  // ═══════════════════════════════════════════════════════════
  //  Native 方法声明
  // ═══════════════════════════════════════════════════════════

  /** 初始化 C++ 引擎 (ImGui 上下文 / shader / 顶点 buffer)。在 GL 线程调。 */
  private native void nativeInit();

  /** 接收场景数据 (源码文本 / 编译库路径 / 场景 JSON)。 */
  private native void nativeUpdateScene(String data);

  /** 视口尺寸变化。在 GL 线程调。 */
  private native void nativeOnResize(int width, int height);

  /** 渲染一帧。在 GL 线程调, 每秒约 60 次。 */
  private native void nativeOnDrawFrame();

  /** 释放全部 native 资源 (ImGui / GL buffer / 纹理 / shader)。 */
  private native void nativeRelease();

  // ═══════════════════════════════════════════════════════════
  //  辅助
  // ═══════════════════════════════════════════════════════════

  /** 加载 libnative_preview_engine.so。用 try-catch 防止 ABI 不匹配时崩溃。 */
  private void loadNativeEngine() {
    if (nativeLibLoaded) return;
    try {
      System.loadLibrary("native_preview_engine");
      nativeLibLoaded = true;
      Log.i(TAG, "libnative_preview_engine.so 加载成功");
    } catch (UnsatisfiedLinkError e) {
      Log.e(TAG, "无法加载 libnative_preview_engine.so — NDK 核不可用", e);
      // NDK 核不可用时强制切到 Web 核
      currentMode = MODE_WEB;
    }
  }

  private static void removeFromParent(View v) {
    if (v != null && v.getParent() instanceof ViewGroup) {
      ((ViewGroup) v.getParent()).removeView(v);
    }
  }

  // ═══════════════════════════════════════════════════════════
  //  JavascriptInterface 桥接对象
  // ═══════════════════════════════════════════════════════════

  /**
   * 暴露给 JS 的桥接对象, JS 端通过 window.AndroidIDE.xxx() 调用。
   *
   * <p>注意: @JavascriptInterface 注解的方法会被 JS 持有引用, 必须确保
   * 不泄漏 Fragment / Activity。所有方法只做轻量回调, 不持有外部引用。
   */
  private class IdeBridge {

    /** Three.js 场景就绪后 JS 主动通知 Java。 */
    @JavascriptInterface
    public void onSceneReady() {
      Log.d(TAG, "Web 核: Three.js 场景就绪");
    }

    /** JS console.log 转发 (备用, WebChromeClient 已处理 console)。 */
    @JavascriptInterface
    public void log(String msg) {
      Log.d("UniversalPreview/JS", msg);
    }

    /** JS 端报错通知。 */
    @JavascriptInterface
    public void onError(String error) {
      Log.e(TAG, "Web 核错误: " + error);
    }
  }

  // ═══════════════════════════════════════════════════════════
  //  GLSurfaceView 子类: 强制 GLES 3.0 EGL 配置
  // ═══════════════════════════════════════════════════════════

  /**
   * 自定义 GLSurfaceView, 选择 GLES 3.0 的 EGLConfig。
   *
   * <p>用 8-bit RGBA + 16-bit depth + 8-bit stencil, 适合 ImGui 渲染。
   */
  private static class GLSurfaceViewGles3 extends android.opengl.GLSurfaceView {

    public GLSurfaceViewGles3(Context context) {
      super(context);
      // setEGLContextClientVersion(3) 在 setEGLContextFactory 之前调
      setEGLContextClientVersion(3);
      // 选择 32-bit RGBA + depth24 + stencil8 的 config
      setEGLConfigChooser(
          new EGLConfigChooser() {
            @Override
            public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
              int[] attribs = {
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 16,
                EGL10.EGL_STENCIL_SIZE, 8,
                EGL10.EGL_RENDERABLE_TYPE, 0x40 /* EGL_OPENGL_ES3_BIT */,
                EGL10.EGL_NONE
              };
              int[] numConfig = new int[1];
              egl.eglChooseConfig(display, attribs, null, 0, numConfig);
              if (numConfig[0] <= 0) {
                // 回退到 GLES2 config
                attribs[13] = 0x4; // EGL_OPENGL_ES2_BIT
                egl.eglChooseConfig(display, attribs, null, 0, numConfig);
              }
              EGLConfig[] configs = new EGLConfig[numConfig[0]];
              egl.eglChooseConfig(display, attribs, configs, configs.length, numConfig);
              return configs.length > 0 ? configs[0] : null;
            }
          });
    }
  }

  /**
   * GLES 3.0 EGL Context 工厂, 确保创建的是 ES3 上下文。
   *
   * <p>GLSurfaceView 默认用 setEGLContextClientVersion(3) 即可, 但某些
   * 设备驱动有 bug, 自定义工厂更可靠。
   */
  private static class Gles3ContextFactory
      implements android.opengl.GLSurfaceView.EGLContextFactory {

    private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
    private static final int EGL_OPENGL_ES3_BIT = 0x40;

    @Override
    public EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig eglConfig) {
      int[] attrib_list = {
        EGL_CONTEXT_CLIENT_VERSION, 3,
        EGL10.EGL_NONE
      };
      return egl.eglCreateContext(display, eglConfig, EGL10.EGL_NO_CONTEXT, attrib_list);
    }

    @Override
    public void destroyContext(EGL10 egl, EGLDisplay display, EGLContext context) {
      egl.eglDestroyContext(display, context);
    }
  }
}
