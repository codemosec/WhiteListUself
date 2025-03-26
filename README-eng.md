# WhiteListUself - Minecraft Whitelist Self-Application Plugin

![Version](https://img.shields.io/badge/Version-0.0.1-blue)
![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21-green)
![Development Framework](https://img.shields.io/badge/Framework-Bukkit/Paper-orange)

[简体中文](README.md) | English

## 📋 Introduction

WhiteListUself is a Minecraft server plugin that allows players to apply for whitelist access through a web page. This plugin is developed based on Bukkit/Paper and is compatible with Minecraft 1.21. It has been successfully tested on servers running 1.21.1-leaves core.This plugin integrates with [mirai](https://github.com/project-mirai) for QQ verification functionality.

## ✨ Main Features

- Built-in web service supporting player self-application for whitelist
- Email verification system that sends verification codes to confirm identity
- Configurable Mirai bot integration (optional feature)
- Simple and user-friendly management interface

## 🚀 Quick Start

### System Requirements

- Minecraft server (Bukkit/Paper core, version 1.21)
- Java 17 or higher
- If you need to use QQ verification, Mirai and mirai-api-http plugin are required

### Installation Steps

1. Download the plugin and place it in the server's `plugins` folder
2. Start and then stop the server, during which the plugin will generate the default configuration file
3. Edit the `plugins/WLUself01/config.yml` configuration file (create it manually if it doesn't exist)
4. Restart the server to complete the installation

## ⚙️ Configuration File

Configuration file example:

```yaml
# Verification Settings
verification.require_qq: false
verification.qq_members_file: "members.json"

# Mirai Connection Settings (only used when verification.require_qq is true)
mirai.websocket_url: "ws://localhost:8080/message"
mirai.verify_key: "your_verify_key"
mirai.bot_qq: 123456789
mirai.group_id: 123456789
mirai.enable_verify: true
mirai.single_mode: true

# Server Settings
server.port: 25565
server.whitelist_command: "whitelist add %player%"

# Verification Code Settings
captcha.length: 6
captcha.expiry_minutes: 5

# Email Settings
email.host: "smtp.example.com"
email.port: 587
email.username: "your_email@example.com"
email.password: "your_password"
email.from: "Minecraft Server <your_email@example.com>"
email.subject: "Minecraft Server Whitelist Verification Code"

# Debug Mode
debug_mode: false
```

## 🔧 Detailed Configuration

### Verification Settings
- `verification.require_qq`: Whether QQ verification is required
- `verification.qq_members_file`: Path to the QQ members list file

### Mirai Connection Settings
- `mirai.websocket_url`: WebSocket interface address for Mirai API HTTP
- `mirai.verify_key`: Verification key for Mirai API HTTP
- `mirai.bot_qq`: Bot QQ number
- `mirai.group_id`: Managed group number
- `mirai.enable_verify`: Whether to enable Mirai verification
- `mirai.single_mode`: Whether to enable single instance mode

### Server Settings
- `server.port`: Port for the plugin's built-in web server
- `server.whitelist_command`: Template for adding whitelist command, `%player%` will be replaced with the player's name

### Verification Code Settings
- `captcha.length`: Verification code length
- `captcha.expiry_minutes`: Verification code validity period (minutes)

### Email Settings
- `email.host`: SMTP server address
- `email.port`: SMTP server port
- `email.username`: Email username
- `email.password`: SMTP authorization code
- `email.from`: Sender information
- `email.subject`: Email subject

## 📝 Usage Instructions

1. Players visit `http://server-IP:configured-port/index.html` to access the application page
2. Fill in the required information (game ID, email, etc.)
3. The system sends a verification code to the player's email
4. The player enters the verification code to complete verification
5. After verification, the player will be automatically added to the server whitelist

## ⚠️ Known Issues

You may encounter the following error message during use:
```
Binding failed, error code: 6, message: The specified operation is not supported
```

This is a known bug but does not affect the normal functionality of the plugin.

## 🔗 Deployment Tutorial
Note: Overall deployment difficulty is high with a significant time investment. Deploying this plugin requires installing Mirai and mirai-api-http. The main challenge lies in connecting Mirai with QQ.

### Basic Deployment
1. Ensure the server meets the installation requirements
2. Make sure Mirai and mirai-api-http plugin are installed (if you need to use QQ verification)
3. Download the plugin and place it in the `plugins` folder
4. Start and then stop the server to generate the configuration file
5. Edit the `plugins/WLUself01/config.yml` configuration file, ensuring the WebSocket port matches the port used by mirai-api-http
6. Restart the server to complete the installation

### Mirai Configuration Notes
The plugin connects to the Mirai bot framework via WebSocket. For detailed configuration of connecting Mirai to QQ, please refer to the official Mirai documentation and online tutorials.

## 📈 Future Plans

- Enhance customization of email content
- Improve Mirai verification functionality
- Enhance error handling mechanisms
- Add optional whitelist web application templates

## 🤝 Contributing

Issues and Pull Requests are welcome to help improve this project!

---

## Author Information

**codemosec**

GitHub: github.com/codemosec  
Bilibili: space.bilibili.com/3546384424766047  
Afdian: afdian.com/a/mosec  
Email: mosec12302@gmail.com
