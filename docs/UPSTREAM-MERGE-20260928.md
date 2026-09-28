# 合并上游 v1.3.4

日期：2026-09-28

## 合并范围

- 当前分支：`fix/performance`，原提交 `84d7022`
- 上游：`upstream/main`，提交 `708606b`（v1.3.4）
- 回退锚点：`backup/fix-performance-before-upstream`
- 本次仅本地合并，不推送远程

## 冲突解决

`ReverieCoreIO.cpp` 保留流式 PNG 任务、受限后台线程池和保存统计；合入上游空图层/空关键帧使用透明 1×1 PNG 的处理，以及分块并行解码、解码队列漏层修复

`PaintRecorder.kt` 保留精确容量分配与快照缓存。锁内复制事件和尺寸、取得快照文件句柄，锁外读取文件；结束会话在同一把锁下摘除文件引用，再删除文件。每次会话使用唯一快照路径，避免新会话覆盖尚在读取的旧文件。读取失败时不生成缺失初始快照的录制流

`libreverie_jni.so` 使用合并后的完整源码在 WSL Debian 重新编译并 strip，未选取任一分支的旧二进制

额外处理自动合并的生命周期衔接：上游新增 `closeDocument()` 在释放文档前取消液化，释放本分支持有的事务、源瓦片快照和图层引用

## 验证

- Qt 6.6.3 / Krita / NDK r25c 原生编译通过
- Kotlin 编译、257 项 JVM 测试、beta Debug APK 构建通过
- 现有 `build/liquify-regression` 宿主程序通过，真实 GLES shader 来自当前工作区；本次未重新编译该测试程序，因为当前分支已不包含原生回归源码/脚本。生产逆向场头文件未在此次合并中修改
- APK 签名 v1/v2 验证通过，包内原生库与重编译文件哈希一致
- `lintDebug` 未通过：133 errors、491 warnings、38 hints；含 Lint 的组合命令因此返回失败，但 `assembleDebug`、Kotlin 编译和单元测试任务已成功。未使用 baseline 或全局屏蔽绕过错误
- `git diff --cached --check` 仅报告上游 `PaintingDialogs.kt` 文件末尾空行，保留上游格式
- 没有执行 Android 真机安装、保存/回放/撤销集成或性能验证
- 先前审查指出的末尾 dab 回读竞态、快速折返丢路径仍为独立待修项，不属于本次冲突解决

## APK

`app/build/outputs/apk/debug/app-debug.apk`

包名 `com.reverie.paint.beta`，版本 `1.3.4-test`（21），arm64-v8a

大小：96,445,118 字节

```text
APK SHA-256: 3133ff3384f7b5d5d870f0da28eb710438c4a617672430a786e33e404c6c6eb4
Native SHA-256: c43fcae57bc19d87b3332df4909a730027b04ed4797a6eff1ec6b3bcc5863017
```

日志：`build/upstream-merge-native.log`、`build/upstream-merge-apk.log`、`build/upstream-merge-liquify.log`、`build/upstream-merge-signature.log`
产物核验：`build/upstream-merge-artifact.json`
