# 贡献说明

## 提交信息

本项目使用 Conventional Commits。

```
<type>(<scope>): <subject>
```

- **type（必须）**：`feat`（新功能）| `fix`（修复）| `docs`（文档）| `style`（格式）| `refactor`（重构）| `chore`（杂项）
- **scope（可选）**：影响范围，见下表
- **subject（必须）**：动词开头，中文不超过 20 字，结尾不加句号

| scope | 范围 |
|---|---|
| `schedule` | 课表与排课 |
| `import` | 导入（教务 / 表格） |
| `dock` | 底栏 |
| `topbar` | 顶栏 |
| `settings` | 设置 |
| `repo` | 仓库杂项 |

示例：

```
feat(schedule): 支持拖动排课
fix(import): 修正整学期导入失败
style(dock): 对齐三种底栏样式
docs(repo): 补充提交规范
```

不要用 `update`、`修改`、`fix bug` 这类没有信息量的标题。提交前会自动校验（见 `.githooks/commit-msg`）。

## 本地启用校验

克隆后执行一次：

```bash
git config core.hooksPath .githooks
git config commit.template .gitmessage
```

## 构建与测试

见 [README.md](README.md)。
