# 贡献指南

感谢你关注本项目。

为了让协作过程更清晰、规范，提交代码或文档时请尽量遵循以下约定。

## 提交前建议

### 1. 保持改动聚焦

一次提交尽量只解决一类问题，例如：

- 修复一个明确 bug
- 增加一个独立功能
- 调整一次文档结构

避免把无关修改混在同一个提交中。

### 2. 提交前先验证

建议至少运行以下命令：

```powershell
mvn -q test
```

如果涉及 Windows 打包，请额外验证：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\package-exe.ps1
```

### 3. 不要提交本地大文件

以下内容默认不应进入仓库：

- 本地 `.nc` 数据文件
- `target/` 构建产物
- IDE 配置目录
- 本地临时图片、日志和缓存

这些内容已经在 `.gitignore` 中配置。

## Issue 规范

提交 Issue 时建议说明：

- 使用的数据文件类型或结构
- 复现步骤
- 实际结果
- 预期结果
- 若有报错，请附上完整错误信息

如果问题与某个具体 `.nc` 文件有关，请尽量说明其维度结构、坐标变量名、连接变量名，而不是只说“打不开”。

## Pull Request 规范

提交 PR 时建议包含：

- 修改目的
- 主要改动点
- 是否影响现有功能
- 是否补充或更新测试
- 是否涉及打包脚本或版本号调整

## 提交信息建议

建议使用清晰的英文前缀加中文或英文描述，例如：

```text
feat: add layered variable export support
fix: 修复 PNG 导出后无法打开的问题
docs: 完善中文 README 与开源说明
chore: update packaging metadata to 1.0.2
```

## 分支建议

建议从 `main` 派生功能分支，例如：

```powershell
git checkout -b feat/export-improvement
```

## 行为约定

- 保持代码风格一致
- 不随意提交无关格式化改动
- 修改公共行为时尽量补充测试
- 修改文档时尽量使用中文，表达清晰、准确

欢迎持续改进本项目。
