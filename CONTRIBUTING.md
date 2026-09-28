# 贡献指南

感谢你对「呼叫转移排班助手」的关注！

## 开发环境

- JDK 17
- Android SDK（compileSdk 34）
- Gradle 8.7（或使用 wrapper）

## 快速开始

```bash
git clone <repo-url>
cd call-forward-scheduler
./gradlew assembleDebug   # 编译
./gradlew test            # 运行单元测试
```

## 提交规范

- 提交信息使用简体中文，遵循 Conventional Commits（如 `feat:`、`fix:`、`docs:`）
- 一行提交信息尽量 ≤ 50 字符

## 代码规范

- Kotlin 官方代码风格
- 新增逻辑请补充单元测试（排班引擎等纯逻辑部分必须覆盖）

## PR 流程

1. Fork 本仓库并创建分支
2. 提交改动
3. 通过 CI（`gradle test` + `assembleDebug`）
4. 发起 Pull Request 并填写模板

## 安全提醒

- 严禁提交任何真实手机号、token、密钥
- 测试呼叫转移请使用测试卡/备用号
