/*
 * native_preview_bridge.cpp — NDK 层 JNI 映射 C++ 代码
 *
 * 编译目标: libnative_preview_engine.so
 *
 * 职责:
 *   1. 图形上下文初始化 (GLES3 shader / 顶点 buffer / Dear ImGui 后端骨架)
 *   2. nativeUpdateScene 接收 Java 层投递的场景数据 (源码 / 库路径 / JSON)
 *   3. 每帧渲染 (nativeOnDrawFrame)
 *   4. nativeRelease 严谨释放全部 GL 资源
 *
 * 线程模型:
 *   - nativeInit / nativeOnResize / nativeOnDrawFrame 在 GLSurfaceView
 *     的 GL 线程调用 (单线程, 无需加锁)
 *   - nativeUpdateScene 可能在 Java 主线程调用, 需要互斥锁保护场景数据
 *   - nativeRelease 在 onDestroyView 调用, 此时 GL 线程可能已退出,
 *     所以要兜底删除 GL 资源 (glDeleteBuffers 等)
 *
 * 极端场景:
 *   - 屏幕旋转: Fragment onDestroyView → nativeRelease, 新 Fragment
 *     重新 nativeInit。C++ 全局状态在 release 后清零, 不会串台。
 *   - 快速切换标签页: nativeUpdateScene 在主线程投递, GL 线程消费,
 *     互斥锁保证数据一致性, 不会读到半截 JSON。
 *   - native 库加载失败: Java 端 try-catch UnsatisfiedLinkError,
 *     C++ 端不会被执行, 安全降级到 Web 核。
 */

#include <jni.h>
#include <android/log.h>
#include <GLES3/gl3.h>
#include <EGL/egl.h>

#include <mutex>
#include <string>
#include <vector>

// ── 日志宏 ──────────────────────────────────────────────────
#define LOG_TAG "UniversalPreview/Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ═══════════════════════════════════════════════════════════
//  全局状态 (单实例, Fragment 生命周期内唯一)
// ═══════════════════════════════════════════════════════════

namespace {

// 互斥锁: 保护 pendingSceneData (主线程写, GL 线程读)
std::mutex g_sceneMutex;

// 待处理的场景数据 (从 Java nativeUpdateScene 投递)
std::string g_pendingSceneData;
bool g_hasPendingData = false;

// GL 资源句柄
struct GLResources {
  bool initialized = false;
  GLuint shaderProgram = 0;
  GLuint vao = 0;
  GLuint vbo = 0;
  GLuint ibo = 0;

  // 视口
  int viewportWidth = 0;
  int viewportHeight = 0;

  // 简单旋转动画状态 (示例: 演示渲染循环在工作)
  float rotationAngle = 0.0f;

  // 帧计数 (用于日志诊断)
  long frameCount = 0;
};

GLResources g_res;

// ── 着色器源码 ──────────────────────────────────────────────
// 简单的 MVP 着色器, 用于渲染示例三角形 / 立方体。
// 实际项目中可替换为完整的 3D 模型 shader 或 ImGui 渲染管线。

constexpr const char* kVertShader = R"(
#version 300 es
precision highp float;
uniform mat4 uMVP;
layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec3 aColor;
out vec3 vColor;
void main() {
  vColor = aColor;
  gl_Position = uMVP * vec4(aPosition, 1.0);
}
)";

constexpr const char* kFragShader = R"(
#version 300 es
precision highp float;
in vec3 vColor;
out vec4 fragColor;
void main() {
  fragColor = vec4(vColor, 1.0);
}
)";

// ── 着色器编译辅助 ──────────────────────────────────────────

GLuint compileShader(GLenum type, const char* src) {
  GLuint shader = glCreateShader(type);
  glShaderSource(shader, 1, &src, nullptr);
  glCompileShader(shader);
  GLint compiled = 0;
  glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
  if (!compiled) {
    GLint infoLen = 0;
    glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &infoLen);
    if (infoLen > 0) {
      std::vector<char> info(infoLen);
      glGetShaderInfoLog(shader, infoLen, nullptr, info.data());
      LOGE("着色器编译失败: %s", info.data());
    }
    glDeleteShader(shader);
    return 0;
  }
  return shader;
}

GLuint linkProgram(GLuint vert, GLuint frag) {
  GLuint program = glCreateProgram();
  glAttachShader(program, vert);
  glAttachShader(program, frag);
  glLinkProgram(program);
  GLint linked = 0;
  glGetProgramiv(program, GL_LINK_STATUS, &linked);
  if (!linked) {
    LOGE("着色器 program 链接失败");
    glDeleteProgram(program);
    return 0;
  }
  return program;
}

// ── 4x4 矩阵 (列主序, OpenGL 约定) ──────────────────────────
// 简单实现, 避免引入 GLM 依赖。仅做旋转 + 正交投影。

void identityMatrix(float* m) {
  for (int i = 0; i < 16; i++) m[i] = 0.0f;
  m[0] = m[5] = m[10] = m[15] = 1.0f;
}

void rotationY(float* m, float angle) {
  float c = cosf(angle);
  float s = sinf(angle);
  identityMatrix(m);
  m[0] = c;  m[2] = s;
  m[8] = -s; m[10] = c;
}

void perspective(float* m, float fov, float aspect, float near, float far) {
  float f = 1.0f / tanf(fov * 0.5f);
  for (int i = 0; i < 16; i++) m[i] = 0.0f;
  m[0] = f / aspect;
  m[5] = f;
  m[10] = (far + near) / (near - far);
  m[11] = -1.0f;
  m[14] = (2.0f * far * near) / (near - far);
}

void multiplyMatrix(float* out, const float* a, const float* b) {
  float tmp[16];
  for (int col = 0; col < 4; col++) {
    for (int row = 0; row < 4; row++) {
      tmp[col * 4 + row] =
          a[0 * 4 + row] * b[col * 4 + 0] +
          a[1 * 4 + row] * b[col * 4 + 1] +
          a[2 * 4 + row] * b[col * 4 + 2] +
          a[3 * 4 + row] * b[col * 4 + 3];
    }
  }
  for (int i = 0; i < 16; i++) out[i] = tmp[i];
}

} // namespace

// ═══════════════════════════════════════════════════════════
//  JNI 方法实现
// ═══════════════════════════════════════════════════════════

extern "C" {

/*
 * nativeInit — 图形上下文初始化
 *
 * 在 GLSurfaceView.Renderer.onSurfaceCreated 回调中调用 (GL 线程)。
 * 职责:
 *   1. 编译 + 链接着色器 program
 *   2. 创建 VAO / VBO / IBO (示例立方体顶点)
 *   3. 初始化 GL 状态 (深度测试 / 剔除面)
 *
 * 注意: 如果已初始化 (屏幕旋转后 GL 线程重新创建), 先释放旧资源。
 */
JNIEXPORT void JNICALL
Java_com_zerostudio_preview_UniversalPreviewEngineFragment_nativeInit(
    JNIEnv* env, jobject thiz) {

  if (g_res.initialized) {
    LOGW("nativeInit: 已初始化, 先释放旧资源");
    // 不在这里调 glDelete — 可能在非 GL 线程, 安全起见只标记
    g_res.initialized = false;
  }

  LOGI("nativeInit: 开始初始化 GLES3 上下文");

  // 编译着色器
  GLuint vert = compileShader(GL_VERTEX_SHADER, kVertShader);
  GLuint frag = compileShader(GL_FRAGMENT_SHADER, kFragShader);
  if (vert == 0 || frag == 0) {
    LOGE("nativeInit: 着色器编译失败, 放弃初始化");
    return;
  }

  g_res.shaderProgram = linkProgram(vert, frag);
  glDeleteShader(vert);
  glDeleteShader(frag);
  if (g_res.shaderProgram == 0) {
    LOGE("nativeInit: program 链接失败");
    return;
  }

  // 示例立方体顶点 (position.xyz + color.rgb, 每顶点 6 float)
  // 36 个顶点 (6 面 × 2 三角形 × 3 顶点), 每面 4 顶点用 indexed draw
  // 这里简化为 24 顶点 + 36 索引
  const float cubeVertices[] = {
    // 前 (红)
    -1,-1, 1,  1,0,0,   1,-1, 1,  1,0,0,   1, 1, 1,  1,0,0,  -1, 1, 1,  1,0,0,
    // 后 (绿)
    -1,-1,-1,  0,1,0,  -1, 1,-1,  0,1,0,   1, 1,-1,  0,1,0,   1,-1,-1,  0,1,0,
    // 左 (蓝)
    -1,-1,-1,  0,0,1,  -1,-1, 1,  0,0,1,  -1, 1, 1,  0,0,1,  -1, 1,-1,  0,0,1,
    // 右 (黄)
     1,-1,-1,  1,1,0,   1, 1,-1,  1,1,0,   1, 1, 1,  1,1,0,   1,-1, 1,  1,1,0,
    // 上 (紫)
    -1, 1,-1,  1,0,1,  -1, 1, 1,  1,0,1,   1, 1, 1,  1,0,1,   1, 1,-1,  1,0,1,
    // 下 (青)
    -1,-1,-1,  0,1,1,   1,-1,-1,  0,1,1,   1,-1, 1,  0,1,1,  -1,-1, 1,  0,1,1,
  };
  const unsigned short cubeIndices[] = {
    0,1,2, 0,2,3,        // 前
    4,5,6, 4,6,7,        // 后
    8,9,10, 8,10,11,     // 左
    12,13,14, 12,14,15,  // 右
    16,17,18, 16,18,19,  // 上
    20,21,22, 20,22,23,  // 下
  };

  // VAO (GLES3 必须用 VAO)
  glGenVertexArrays(1, &g_res.vao);
  glBindVertexArray(g_res.vao);

  // VBO
  glGenBuffers(1, &g_res.vbo);
  glBindBuffer(GL_ARRAY_BUFFER, g_res.vbo);
  glBufferData(GL_ARRAY_BUFFER, sizeof(cubeVertices), cubeVertices, GL_STATIC_DRAW);

  // IBO
  glGenBuffers(1, &g_res.ibo);
  glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, g_res.ibo);
  glBufferData(GL_ELEMENT_ARRAY_BUFFER, sizeof(cubeIndices), cubeIndices, GL_STATIC_DRAW);

  // 顶点属性: position (location=0, 3 float) + color (location=1, 3 float)
  glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 6 * sizeof(float), (void*)0);
  glEnableVertexAttribArray(0);
  glVertexAttribPointer(1, 3, GL_FLOAT, GL_FALSE, 6 * sizeof(float), (void*)(3 * sizeof(float)));
  glEnableVertexAttribArray(1);

  glBindVertexArray(0);

  // GL 状态
  glEnable(GL_DEPTH_TEST);
  glEnable(GL_CULL_FACE);
  glCullFace(GL_BACK);
  glClearColor(0.1f, 0.1f, 0.12f, 1.0f);

  g_res.initialized = true;
  g_res.frameCount = 0;
  LOGI("nativeInit: 完成 (shader=%u, vao=%u, vbo=%u, ibo=%u)",
       g_res.shaderProgram, g_res.vao, g_res.vbo, g_res.ibo);
}

/*
 * nativeUpdateScene — 接收场景数据
 *
 * 在 Java 主线程调用 (dispatchSourceData type=1)。
 * payload 可以是:
 *   - 源码文本 (C/C++ 代码, 用于算法轨迹可视化)
 *   - 编译后库路径 (.so 路径, 用于 dlopen 动态加载渲染插件)
 *   - 场景 JSON (节点树 / 变换矩阵 / 材质)
 *
 * 线程安全: 用互斥锁保护, GL 线程在 onDrawFrame 中安全读取。
 */
JNIEXPORT void JNICALL
Java_com_zerostudio_preview_UniversalPreviewEngineFragment_nativeUpdateScene(
    JNIEnv* env, jobject thiz, jstring data) {

  const char* chars = env->GetStringUTFChars(data, nullptr);
  if (chars == nullptr) {
    LOGW("nativeUpdateScene: payload 为 null");
    return;
  }

  std::string payload(chars);
  env->ReleaseStringUTFChars(data, chars);

  {
    std::lock_guard<std::mutex> lock(g_sceneMutex);
    g_pendingSceneData = std::move(payload);
    g_hasPendingData = true;
  }

  LOGI("nativeUpdateScene: 已接收场景数据 (%zu bytes)", g_pendingSceneData.size());
}

/*
 * nativeOnResize — 视口尺寸变化
 *
 * 在 GL 线程调用 (onSurfaceChanged)。
 */
JNIEXPORT void JNICALL
Java_com_zerostudio_preview_UniversalPreviewEngineFragment_nativeOnResize(
    JNIEnv* env, jobject thiz, jint width, jint height) {

  g_res.viewportWidth = width;
  g_res.viewportHeight = height;
  glViewport(0, 0, width, height);
  LOGI("nativeOnResize: %d x %d", width, height);
}

/*
 * nativeOnDrawFrame — 每帧渲染
 *
 * 在 GL 线程调用, 每秒约 60 次 (RENDERMODE_CONTINUOUSLY)。
 */
JNIEXPORT void JNICALL
Java_com_zerostudio_preview_UniversalPreviewEngineFragment_nativeOnDrawFrame(
    JNIEnv* env, jobject thiz) {

  if (!g_res.initialized) return;

  // ── 消费待处理场景数据 ──
  {
    std::lock_guard<std::mutex> lock(g_sceneMutex);
    if (g_hasPendingData) {
      // TODO: 解析 g_pendingSceneData, 更新场景节点 / 加载模型 / 刷新 ImGui 面板
      // 当前骨架只 log 一次
      LOGI("onDrawFrame: 消费场景数据 (%zu bytes)", g_pendingSceneData.size());
      g_hasPendingData = false;
    }
  }

  // ── 清屏 ──
  glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

  // ── 渲染示例立方体 (旋转动画) ──
  glUseProgram(g_res.shaderProgram);
  glBindVertexArray(g_res.vao);

  // 计算 MVP 矩阵
  float rot[16], proj[16], mvp[16];
  g_res.rotationAngle += 0.02f;
  rotationY(rot, g_res.rotationAngle);
  float aspect = g_res.viewportWidth > 0
      ? (float) g_res.viewportWidth / g_res.viewportHeight
      : 1.0f;
  perspective(proj, 0.9f, aspect, 0.1f, 100.0f);
  multiplyMatrix(mvp, proj, rot);

  // 上传 MVP uniform
  GLint mvpLoc = glGetUniformLocation(g_res.shaderProgram, "uMVP");
  glUniformMatrix4fv(mvpLoc, 1, GL_FALSE, mvp);

  // 绘制
  glDrawElements(GL_TRIANGLES, 36, GL_UNSIGNED_SHORT, 0);

  glBindVertexArray(0);

  // ── Dear ImGui 渲染 (骨架) ──
  // TODO: 接入 ImGui OpenGL ES3 后端
  //   1. ImGui::CreateContext()
  //   2. ImGui_ImplAndroidGLES3_Init()
  //   3. ImGui::NewFrame() / ImGui::Render() / ImGui_ImplOpenGL3_RenderDrawData()
  // 当前骨架只渲染示例立方体, ImGui 留扩展点。

  g_res.frameCount++;
  if (g_res.frameCount % 300 == 0) {
    LOGI("onDrawFrame: 已渲染 %ld 帧", g_res.frameCount);
  }
}

/*
 * nativeRelease — 严谨释放全部 native 资源
 *
 * 在 Fragment.onDestroyView 调用。
 * 此时 GL 线程可能已退出 (GLSurfaceView.onPause 后), 也可能还在,
 * 所以 glDelete 调用要防御性处理 (无效句柄 glDelete 会静默忽略)。
 *
 * 释放顺序:
 *   1. GL 资源 (buffer / shader / program)
 *   2. 场景数据 (清空 pending)
 *   3. ImGui 上下文 (TODO)
 */
JNIEXPORT void JNICALL
Java_com_zerostudio_preview_UniversalPreviewEngineFragment_nativeRelease(
    JNIEnv* env, jobject thiz) {

  LOGI("nativeRelease: 开始释放资源");

  // 1. GL 资源 (防御性: 句柄为 0 时 glDelete 静默忽略, 安全)
  if (g_res.vbo != 0) {
    glDeleteBuffers(1, &g_res.vbo);
    g_res.vbo = 0;
  }
  if (g_res.ibo != 0) {
    glDeleteBuffers(1, &g_res.ibo);
    g_res.ibo = 0;
  }
  if (g_res.vao != 0) {
    glDeleteVertexArrays(1, &g_res.vao);
    g_res.vao = 0;
  }
  if (g_res.shaderProgram != 0) {
    glDeleteProgram(g_res.shaderProgram);
    g_res.shaderProgram = 0;
  }

  // 2. 清空场景数据
  {
    std::lock_guard<std::mutex> lock(g_sceneMutex);
    g_pendingSceneData.clear();
    g_pendingSceneData.shrink_to_fit();
    g_hasPendingData = false;
  }

  // 3. ImGui 上下文释放 (TODO: 接入后补 ImGui::DestroyContext())

  g_res.initialized = false;
  g_res.frameCount = 0;
  g_res.rotationAngle = 0.0f;

  LOGI("nativeRelease: 全部资源已释放");
}

} // extern "C"

// ═══════════════════════════════════════════════════════════
//  JNI_OnLoad — 库加载时调一次
// ═══════════════════════════════════════════════════════════

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
  LOGI("JNI_OnLoad: libnative_preview_engine.so 已加载");
  return JNI_VERSION_1_6;
}
