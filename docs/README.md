# 红隼锐瞳 - 代码沙箱

## 项目简介

为红隼锐瞳在线判题系统实现的基于 Java 的代码沙箱，用于将用户提供的代码进行编译、运行、整理输出，并返回结果。

可为其他服务调用。

## 输入输出

执行请求(输入)
* 输入列表
* 代码
* 语言

执行响应(输出)
* 输出列表
* 错误信息
* 执行状态
    * 执行成功，1
    * 执行失败，2
    * 沙箱错误，3
* 判题信息
    * 消息
    * 消耗内存
    * 消耗时间

## 其他

红隼锐瞳在线判题系统-后端仓库地址：[https://github.com/Towerrrr/KestrelOJ-backend-microservice](https://github.com/Towerrrr/KestrelOJ-backend-microservice)