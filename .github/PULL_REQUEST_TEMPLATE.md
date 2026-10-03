## 变更概述 (Summary)

<!-- 简要说明本 PR 解决的问题或引入的新能力，按 Conventional Commits 关联 Issue -->
Fixes #

## 变更类型 (Type of Change)

- [ ] `feat`: 新增功能 (Feature)
- [ ] `fix`: 缺陷修复 (Bug Fix)
- [ ] `perf`: 性能优化 (Performance Optimization)
- [ ] `refactor`: 代码重构 (Refactoring without logic change)
- [ ] `docs`: 文档或多语言文案更新 (Documentation / Strings)
- [ ] `build` / `chore`: 构建系统或工程配置调整 (Build / Maintenance)

## 架构与铁律自检 (Architecture Checklist)

修改核心或渲染代码时，请确认未违反 `AGENTS.md` §4 架构铁律:

- [ ] **线程模型**: 文档与渲染操作未进入 UI 线程，均由 Handler 异步调度
- [ ] **双缓冲机制**: 写像素线程与 Compose 读取的 Bitmap 维持前后双缓冲隔离
- [ ] **热路径零分配**: 笔触采集、渲染分发、录制编解码热路径上未引入逐帧临时对象与字符串拼接
- [ ] **手势隔离**: `pointerInput` 探测器未以 zoom/pan/rotation 为 key，手势不会第一帧中断
- [ ] **文案国际化**: 新增或修改文案已使用 `strings.xml` 管理，并同步提供中英文翻译 (`values/` 与 `values-en/`)

## 验证与测试 (Verification & Testing)

请勾选已执行的自测流程并注明测试设备:

- [ ] 机械编译自检通过 (`./gradlew :app:compileDebugKotlin`)
- [ ] JVM 单元测试通过 (`./gradlew :app:testDebugUnitTest`)
- [ ] 真机验证通过 (请填写测试机型与手写笔硬件型号: ________)
