#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <unistd.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <signal.h>
#include <errno.h>
#include <string.h>
#include <fcntl.h>
#include <pthread.h>
#include <stdio.h>

#define LOG_TAG "vmbridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static pid_t g_vm_pid = -1;

static std::string g_log_path;

struct WaitArgs {
    pid_t pid;
    std::string logPath;
};

static void* wait_thread(void* arg) {
    WaitArgs* wa = static_cast<WaitArgs*>(arg);
    int status = 0;
    waitpid(wa->pid, &status, 0);
    if (!wa->logPath.empty()) {
        int fd = open(wa->logPath.c_str(), O_CREAT | O_WRONLY | O_APPEND, 0644);
        if (fd >= 0) {
            if (WIFEXITED(status)) {
                dprintf(fd, "QEMU exited with code %d\n", WEXITSTATUS(status));
            } else if (WIFSIGNALED(status)) {
                dprintf(fd, "QEMU killed by signal %d\n", WTERMSIG(status));
            } else {
                dprintf(fd, "QEMU exited (status=%d)\n", status);
            }
            close(fd);
        }
    }
    delete wa;
    return nullptr;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_cryptd_vm_NativeBridge_startVm(
        JNIEnv* env,
        jclass clazz,
        jstring isoPath,
        jstring qemuPath,
        jstring libDir,
        jstring shareDir,
        jstring diskPath,
        jstring logPath,
        jint ramMb,
        jint cpuCores,
        jstring gfx,
        jint vncPort,
        jboolean useKvm) {

    const char* iso = env->GetStringUTFChars(isoPath, nullptr);
    const char* qemu = env->GetStringUTFChars(qemuPath, nullptr);
    const char* libDirStr = env->GetStringUTFChars(libDir, nullptr);
    const char* shareDirStr = env->GetStringUTFChars(shareDir, nullptr);
    const char* diskPathStr = env->GetStringUTFChars(diskPath, nullptr);
    const char* logPathStr = env->GetStringUTFChars(logPath, nullptr);
    const char* gfxStr = env->GetStringUTFChars(gfx, nullptr);

    LOGI("startVm iso=%s ram=%d cpu=%d gfx=%s vnc=%d kvm=%d libDir=%s shareDir=%s disk=%s log=%s",
         iso, ramMb, cpuCores, gfxStr, vncPort, useKvm, libDirStr, shareDirStr, diskPathStr, logPathStr);

    if (g_vm_pid > 0) {
        LOGE("VM already running pid=%d", g_vm_pid);
        env->ReleaseStringUTFChars(isoPath, iso);
        env->ReleaseStringUTFChars(qemuPath, qemu);
        env->ReleaseStringUTFChars(libDir, libDirStr);
        env->ReleaseStringUTFChars(shareDir, shareDirStr);
        env->ReleaseStringUTFChars(diskPath, diskPathStr);
        env->ReleaseStringUTFChars(logPath, logPathStr);
        env->ReleaseStringUTFChars(gfx, gfxStr);
        return -2;
    }

    std::string vncDisplay = "127.0.0.1:" + std::to_string(vncPort - 5900);
    std::string ramArg = std::to_string(ramMb);
    std::string cpuArg = std::to_string(cpuCores);

    std::vector<std::string> args = {
        qemu,
        "-L", std::string(shareDirStr) + "/qemu",
        "-machine", "virt",
        "-cpu", "cortex-a57",
        "-m", ramArg,
        "-smp", cpuArg,
        "-display", "none",
        "-vnc", vncDisplay,
        "-netdev", "user,id=net0",
        "-device", "virtio-net-pci,netdev=net0"
    };

    args.push_back("-device");
    args.push_back("virtio-gpu-pci");

    std::string isoStr(iso);
    std::string diskStr(diskPathStr ? diskPathStr : "");
    args.push_back("-bios");
    args.push_back(std::string(shareDirStr) + "/qemu/edk2-aarch64-code.fd");

    auto isQcow2 = [](const std::string& p) {
        return p.size() >= 6 && p.substr(p.size() - 6) == ".qcow2";
    };
    auto isRaw = [](const std::string& p) {
        return p.size() >= 4 && (p.substr(p.size() - 4) == ".img" || p.substr(p.size() - 4) == ".raw");
    };

    if (!diskStr.empty()) {
        if (isQcow2(diskStr)) {
            args.push_back("-drive");
            args.push_back("file=" + diskStr + ",if=virtio,format=qcow2");
        } else if (isRaw(diskStr)) {
            args.push_back("-drive");
            args.push_back("file=" + diskStr + ",if=virtio,format=raw");
        }
    }

    if (!isoStr.empty()) {
        if (isQcow2(isoStr) || isRaw(isoStr)) {
            args.push_back("-drive");
            args.push_back("file=" + isoStr + ",if=virtio,format=" + std::string(isQcow2(isoStr) ? "qcow2" : "raw"));
        } else {
            args.push_back("-drive");
            args.push_back("file=" + isoStr + ",if=virtio,media=cdrom,format=raw");
            if (!diskStr.empty()) {
                args.push_back("-boot");
                args.push_back("order=d");
            }
        }
    }

    if (useKvm && access("/dev/kvm", R_OK | W_OK) == 0) {
        args.push_back("-accel");
        args.push_back("kvm");
    } else {
        args.push_back("-accel");
        args.push_back("tcg,thread=multi");
    }

    std::vector<char*> argv;
    argv.reserve(args.size() + 1);
    for (auto& s : args) {
        argv.push_back(const_cast<char*>(s.c_str()));
    }
    argv.push_back(nullptr);

    pid_t pid = fork();
    if (pid == 0) {
        setenv("LD_LIBRARY_PATH", libDirStr, 1);
        setenv("QEMU_AUDIO_DRV", "none", 1);
        if (logPathStr && strlen(logPathStr) > 0) {
            int fd = open(logPathStr, O_CREAT | O_WRONLY | O_APPEND, 0644);
            if (fd >= 0) {
                dup2(fd, 1);
                dup2(fd, 2);
                dprintf(fd, "QEMU args:\n");
                for (size_t i = 0; i < argv.size() && argv[i]; i++) {
                    dprintf(fd, "  %s\n", argv[i]);
                }
                close(fd);
            }
        }
        execv(qemu, argv.data());
        if (logPathStr && strlen(logPathStr) > 0) {
            int fd = open(logPathStr, O_CREAT | O_WRONLY | O_APPEND, 0644);
            if (fd >= 0) {
                dprintf(fd, "execv failed: %s\n", strerror(errno));
                close(fd);
            }
        }
        _exit(127);
    } else if (pid > 0) {
        g_vm_pid = pid;
        LOGI("VM started pid=%d", g_vm_pid);
        if (logPathStr && strlen(logPathStr) > 0) {
            pthread_t tid;
            WaitArgs* wa = new WaitArgs{pid, std::string(logPathStr)};
            if (pthread_create(&tid, nullptr, wait_thread, wa) == 0) {
                pthread_detach(tid);
            }
        }
    } else {
        LOGE("fork failed");
    }

    env->ReleaseStringUTFChars(isoPath, iso);
    env->ReleaseStringUTFChars(qemuPath, qemu);
    env->ReleaseStringUTFChars(libDir, libDirStr);
    env->ReleaseStringUTFChars(shareDir, shareDirStr);
    env->ReleaseStringUTFChars(diskPath, diskPathStr);
    env->ReleaseStringUTFChars(logPath, logPathStr);
    env->ReleaseStringUTFChars(gfx, gfxStr);

    if (pid <= 0) {
        return -3;
    }
    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_cryptd_vm_NativeBridge_stopVm(JNIEnv*, jclass) {
    LOGI("stopVm");
    if (g_vm_pid > 0) {
        kill(g_vm_pid, SIGTERM);
        waitpid(g_vm_pid, nullptr, 0);
        g_vm_pid = -1;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_cryptd_vm_NativeBridge_sendMouseEvent(JNIEnv*, jclass, jint dx, jint dy, jint buttons) {
    LOGI("mouse dx=%d dy=%d buttons=%d", dx, dy, buttons);
}

extern "C" JNIEXPORT void JNICALL
Java_com_cryptd_vm_NativeBridge_sendKeyEvent(JNIEnv*, jclass, jint keyCode, jboolean isDown) {
    LOGI("key code=%d down=%d", keyCode, isDown);
}
