# WhiteListUself - 我的世界白名单自助申请插件

![版本](https://img.shields.io/badge/版本-0.0.1-blue)
![Minecraft版本](https://img.shields.io/badge/Minecraft-1.21-green)
![开发框架](https://img.shields.io/badge/开发框架-Bukkit/Paper-orange)

简体中文 | [ENGLISH](https://github.com/codemosec/WhiteListUself/blob/main/README-eng.md)
## 致谢声明
本项目派生自xiaoqiannb/webwhitelist-for-minecraft开源项目并受其启发。特此致谢原作者的杰出工作及其对本项目基础构建所做的核心贡献。
## 📋 简介

WhiteListUself 是一个允许玩家通过网页自助申请白名单的 Minecraft 服务器插件。本插件基于 Bukkit/Paper 开发，适用于 Minecraft 1.21 版本，已在核心为 1.21.1-leaves 的服务器上成功测试运行。本插件通过对接[mirai](https://github.com/project-mirai)框架，实现QQ群验证功能。

## ✨ 主要功能

- 提供内置网页服务，支持玩家自助申请白名单
- 邮箱验证系统，向用户发送验证码确认身份
- 可配置的 Mirai 机器人集成（可选功能）
- 简单易用的管理界面

## 🚀 快速开始

### 系统要求

- Minecraft 服务器（Bukkit/Paper 核心，版本 1.21）
- Java 17 或更高版本
- 如需使用 QQ 验证功能，需安装 Mirai 及 mirai-api-http 插件

### 安装步骤

1. 下载插件并放置在服务器的 `plugins` 文件夹中
2. 启动服务器后关闭，此时插件将生成默认配置文件
3. 编辑 `plugins/WLUself01/config.yml` 配置文件（如不存在请手动创建）
4. 重启服务器完成安装

## ⚙️ 配置文件

配置文件示例：

```yaml
# 验证设置
verification.require_qq: false
verification.qq_members_file: "members.json"

# Mirai连接设置 (仅当verification.require_qq为true时使用)
mirai.websocket_url: "ws://localhost:8080/message"
mirai.verify_key: "your_verify_key"
mirai.bot_qq: 123456789
mirai.group_id: 123456789
mirai.enable_verify: true
mirai.single_mode: true

# 服务器设置
server.port: 25565
server.whitelist_command: "whitelist add %player%"

# 验证码设置
captcha.length: 6
captcha.expiry_minutes: 5

# 邮箱设置
email.host: "smtp.example.com"
email.port: 587
email.username: "your_email@example.com"
email.password: "your_password"
email.from: "Minecraft Server <your_email@example.com>"
email.subject: "Minecraft服务器白名单验证码"

# 调试模式
debug_mode: false
```

## 🔧 详细配置说明

### 验证设置
- `verification.require_qq`: 是否需要 QQ 验证
- `verification.qq_members_file`: QQ 成员列表文件路径

### Mirai 连接设置
- `mirai.websocket_url`: Mirai API HTTP 的 WebSocket 接口地址
- `mirai.verify_key`: Mirai API HTTP 的验证密钥
- `mirai.bot_qq`: 机器人 QQ 号
- `mirai.group_id`: 管理的群号
- `mirai.enable_verify`: 是否启用 Mirai 验证
- `mirai.single_mode`: 是否启用单例模式

### 服务器设置
- `server.port`: 插件内置网页服务器端口
- `server.whitelist_command`: 添加白名单命令模板，`%player%` 将被替换为玩家名

### 验证码设置
- `captcha.length`: 验证码长度
- `captcha.expiry_minutes`: 验证码有效期（分钟）

### 邮箱设置
- `email.host`: SMTP 服务器地址
- `email.port`: SMTP 服务器端口
- `email.username`: 邮箱用户名
- `email.password`: SMTP授权码
- `email.from`: 发件人信息
- `email.subject`: 邮件主题

## 📝 使用说明

1. 玩家访问 `http://服务器IP:配置的端口/index.html` 进入申请页面
2. 填写必要信息（游戏ID、邮箱等）
3. 系统向玩家邮箱发送验证码
4. 玩家输入验证码完成验证
5. 验证通过后，玩家将被自动添加到服务器白名单

## ⚠️ 已知问题

使用过程中可能会出现以下报错信息：
```
绑定失败，错误码: 6, 消息: 指定操作不支持
```

这是一个已知的 bug，但不影响插件的正常功能使用。

## 🔗 部署教程
注：整体部署难度大、时间成本高。部署该插件需安装mirai、mirai-api-http、难点在于进行mirai与QQ的连接。
### 基础部署
1. 确认服务器满足安装条件
2. 确保已安装 Mirai 及 mirai-api-http 插件（如需使用 QQ 验证功能）
3. 下载插件并放置在 `plugins` 文件夹内
4. 启动服务器后关闭，生成配置文件
5. 编辑 `plugins/WLUself01/config.yml` 配置文件，确保 WebSocket 端口与 mirai-api-http 的端口一致
6. 重启服务器完成安装

### Mirai 配置说明
插件通过 WebSocket 连接 Mirai 机器人框架。目前关于 Mirai 连接 QQ 的详细配置，请自行参考 Mirai 官方文档及网上教程。

## 📈 未来计划

- 增进对邮件内容的自定义
- 完善连接mirai验证功能
- 改进错误处理机制
- 添加可选的白名单网页申请模板

## 🤝 参与贡献

欢迎提交 Issues 和 Pull Requests 来完善本项目！

---

## 作者信息

**CodeMoSec**

GitHub: github.com/codemosec
Bilibili: space.bilibili.com/3546384424766047
爱发电: afdian.com/a/mosec
电子邮件: mosec12302@gmail.com
