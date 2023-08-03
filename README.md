<div align="center">

# GMSR群机器人

![](https://img.shields.io/github/languages/top/CuteReimu/maple-bots "语言")
[![](https://img.shields.io/github/actions/workflow/status/CuteReimu/maple-bots/build.yml?branch=master)](https://github.com/CuteReimu/maple-bots/actions/workflows/build.yml "代码分析")
[![](https://img.shields.io/github/contributors/CuteReimu/maple-bots)](https://github.com/CuteReimu/maple-bots/graphs/contributors "贡献者")
[![](https://img.shields.io/github/license/CuteReimu/maple-bots)](https://github.com/CuteReimu/maple-bots/blob/master/LICENSE "许可协议")
</div>

## 编译

```shell
./gradlew buildPlugin
```

在`build/mirai`下可以找到编译好的jar包，即为Mirai插件

## 使用方法

1. 首先了解、安装并启动 [Mirai - Console Terminal](https://github.com/mamoe/mirai/blob/dev/docs/ConsoleTerminal.md) 。
   如有必要，你可能需要修改 `config/Console` 下的 Mirai 相关配置。
   **QQ登录、收发消息相关全部使用 Mirai 框架，若一步出现了问题，请去Mirai的repo或者社区寻找解决方案。**
2. 启动Mirai后，会发现生成了很多文件夹。将编译得到的插件jar包放入 `plugins` 文件夹后，重启Mirai。

## 配置

第一次启动后，会在 `config/net.cutereimu.maplebots` 下生成相关配置文件。

* 配置文件 `Config.yml` ：

```yaml
# 生效的QQ群
qq_groups:
  - 12345678
```

支持动态增删生效的QQ群，只需私聊机器人`增加冒险岛QQ群 12345678`和`删除冒险岛QQ群 12345678`即可

* 配置文件 `DefaultQunDb.yml` 用于从其它机器人导入词条，这里就不介绍了。

根据自己的需要修改配置文件后，重启 Mirai 即可。


## 开发相关

如果你想要本地调试，执行如下命令即可：

```shell
./gradlew runConsole
```

上述命令会自动下载Mirai Console并运行，即可本地调试。本地调试时会生成一个`debug-sandbox`文件夹，和Mirai Console的文件夹结构基本相同，
