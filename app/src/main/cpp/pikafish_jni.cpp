// JNI bridge to Pikafish (GPLv3, https://github.com/official-pikafish/Pikafish).
// See THIRD_PARTY_NOTICES.md and LICENSE at the project root.
#include <jni.h>
#include <string>
#include <mutex>
#include <memory>
#include <vector>
#include <unistd.h>
#include <android/log.h>
#include "Pikafish-Pikafish-2026-01-02/src/bitboard.h"
#include "Pikafish-Pikafish-2026-01-02/src/position.h"
#include "Pikafish-Pikafish-2026-01-02/src/uci.h"
#include "Pikafish-Pikafish-2026-01-02/src/misc.h"

using namespace Stockfish;

static JavaVM* g_jvm = nullptr;
static jobject g_callback_obj = nullptr;
static std::unique_ptr<UCIEngine> g_uci = nullptr;
static std::mutex g_init_mutex;

extern "C" void jni_callback_wrapper(const char* msg) {
    if (!g_jvm || !g_callback_obj || !msg) return;

    JNIEnv* env;
    bool attached = false;
    jint res = g_jvm->GetEnv((void**)&env, JNI_VERSION_1_6);
    if (res == JNI_EDETACHED) {
        if (g_jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
        attached = true;
    } else if (res != JNI_OK) {
        return;
    }

    jclass clazz = env->GetObjectClass(g_callback_obj);
    jmethodID method = env->GetMethodID(clazz, "onEngineOutput", "(Ljava/lang/String;)V");
    if (method) {
        jstring jmsg = env->NewStringUTF(msg);
        env->CallVoidMethod(g_callback_obj, method, jmsg);
        env->DeleteLocalRef(jmsg);
    } else {
        __android_log_print(ANDROID_LOG_ERROR, "PikafishJNI", "Failed to find onEngineOutput method");
    }

    if (attached) {
        g_jvm->DetachCurrentThread();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_yieye_xiangqi_EngineHelper_initJNI(JNIEnv* env, jobject thiz, jstring jworkDir) {
    std::lock_guard<std::mutex> lock(g_init_mutex);

    if (g_uci) return;

    const char* work_dir_ptr = env->GetStringUTFChars(jworkDir, nullptr);
    std::string work_dir(work_dir_ptr);
    env->ReleaseStringUTFChars(jworkDir, work_dir_ptr);

    // Change working directory so engine can find pikafish.nnue if it's there
    chdir(work_dir.c_str());

    env->GetJavaVM(&g_jvm);
    g_callback_obj = env->NewGlobalRef(thiz);

    set_jni_callback(jni_callback_wrapper);

    Bitboards::init();
    Position::init();

    // Workaround for argv[0] - pass the workDir as binary path
    std::string binaryPath = work_dir + "/pikafish";
    static std::string g_binaryPath;
    g_binaryPath = binaryPath;
    char* argv_p[] = {(char*)g_binaryPath.c_str(), nullptr};
    g_uci = std::make_unique<UCIEngine>(1, argv_p);

    // Trigger initial UCI output
    g_uci->process_command("uci");
}

extern "C" JNIEXPORT void JNICALL
Java_com_yieye_xiangqi_EngineHelper_sendCommandJNI(JNIEnv* env, jobject thiz, jstring jcmd) {
    if (!g_uci) return;

    const char* cmd_ptr = env->GetStringUTFChars(jcmd, nullptr);
    if (!cmd_ptr) return;
    std::string cmd(cmd_ptr);
    env->ReleaseStringUTFChars(jcmd, cmd_ptr);

    g_uci->process_command(cmd);
}
