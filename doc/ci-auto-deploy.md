# kkFileView master 自动部署

当前线上 Windows 服务器的实际部署信息如下：

- 部署根目录：`C:\kkFileView-5.0`
- 运行 jar：`C:\kkFileView-5.0\bin\kkFileView-5.0.jar`
- 启动脚本：`C:\kkFileView-5.0\bin\startup.bat`
- 运行配置：`C:\kkFileView-5.0\config\test.properties`
- 健康检查地址：`http://127.0.0.1:8012/`

服务器当前没有安装 `git` 和 `mvn`，因此自动部署链路采用：

1. GitHub Actions 在 `master` 合并后构建 `kkFileView-*.jar`
2. 由 GitHub Actions runner 解析当前 workflow artifact 的临时下载地址
3. 通过 WinRM 连接 Windows 服务器
4. 由服务器通过临时下载地址拉取 jar artifact
5. 备份线上 jar，替换为新版本
6. 使用现有 `startup.bat` 重启，并做健康检查
7. 如果健康检查失败，则自动回滚旧 jar 并重新拉起

这样做的目的是不把 GitHub token 下发到生产服务器，服务器只接触一次性 artifact 下载链接。

## 需要配置的 GitHub Secrets

- `KK_DEPLOY_HOST`
- `KK_DEPLOY_USERNAME`
- `KK_DEPLOY_PASSWORD`

下面这些可以不配，未配置时会使用默认值：

- `KK_DEPLOY_PORT=5985`
- `KK_DEPLOY_ROOT=C:\kkFileView-5.0`
- `KK_DEPLOY_HEALTH_URL=http://127.0.0.1:8012/`

## Workflow

新增 workflow：`.github/workflows/master-auto-deploy.yml`

- 触发条件：`push` 到 `master`，或手动 `workflow_dispatch`
- 构建产物：`kkfileview-server-jar`
- 部署方式：WinRM + runner 侧解析 artifact 临时下载地址 + Windows 服务器拉取 artifact
