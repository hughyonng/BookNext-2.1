# CLAUDE.md

## 项目信息
- **项目名称**：BookNext
- **类型**：Android 应用 (Kotlin + Gradle)

## 自动工作流规则

根据我的问题性质，自动判断是否启用以下插件，**不需要我手动输入 `/feature-dev` 或 `/superpowers`**：

### 触发规则

| 当我的问题/任务涉及 | 自动启用 |
|---|---|
| 开发新功能、实现需求、添加模块 | `/feature-dev` — 使用完整的 7 阶段特性开发工作流 |
| 重构代码、需要高质量/TDD 编码、调研设计 | `/superpowers` — 使用 TDD 开发框架，自动包含浏览器控制和多代理协作 |
| Bug 修复 | 先分析范围，必要时自动调用 `/feature-dev` 或 `/superpowers` |
| 需要规划/分解任务 | `/superpowers` — 利用其 plan-execute 和并行子代理工作流 |
| 其他（文档、配置、查询等） | 正常处理，不启用插件 |

### 重要说明

- 不要问我"要不要用 feature-dev"或"是否启用 superpowers"——直接按规则自动执行
- 如果任务涉及多个方面，优先使用匹配度最高的插件
- 不确定时，默认启用 `/feature-dev` 以保证开发质量
