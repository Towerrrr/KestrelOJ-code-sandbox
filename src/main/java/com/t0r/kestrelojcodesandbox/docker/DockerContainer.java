package com.t0r.kestrelojcodesandbox.docker;

import cn.hutool.core.io.FileUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.model.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Data
@RequiredArgsConstructor
@AllArgsConstructor
@Slf4j
public class DockerContainer {

    private final String containerId;
    private final String containersCodeAbsolutePath;
    private TaskStatusContainerStatus status; // todo 状态？
    private LocalDateTime lastUsedTime;

    // todo 解耦，分成DockerContainer和DockerContainers

    /**
     * 创建并启动容器
     *
     * @param dockerClient
     * @param image
     * @return 容器ID
     */
    public static DockerContainer createAndStart(DockerClient dockerClient, String image) {
        // 创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);

        HostConfig hostConfig = new HostConfig();
        hostConfig.withMemory(1024 * 1024 * 1024L);
        hostConfig.withMemorySwap(0L);
        hostConfig.withCpuCount(1L);
        // todo 安全管理配置，直接用读取配置文件的方式，后续再看能否优化
        String userDir = System.getProperty("user.dir");
        String seccompConfigPath = "src/main/java/com/t0r/kestrelojcodesandbox/security/seccomp.json";
        String seccompConfigAbsolutePath = userDir + File.separator + seccompConfigPath;
        String seccompConfig = FileUtil.readUtf8String(seccompConfigAbsolutePath);
        hostConfig.withSecurityOpts(Collections.singletonList("seccomp=" + seccompConfig));

        String containersCodePath = "containersCode" + File.separator + UUID.randomUUID();
        String containersCodeAbsolutePath = userDir + File.separator + containersCodePath;
        // 创建容器代码目录
        FileUtil.mkdir(containersCodeAbsolutePath);
        hostConfig.setBinds(new Bind(containersCodeAbsolutePath, new Volume("/code")));

        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)
                .withNetworkDisabled(true)
                .withReadonlyRootfs(true) // 根文件系统设为只读
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withTty(true)
                .exec();
        log.info("创建容器：{}", createContainerResponse);
        String containerId = createContainerResponse.getId();

        // 启动容器
        StartContainerCmd startContainerCmd = dockerClient.startContainerCmd(containerId);
        startContainerCmd.exec();
        log.info("启动容器：{}", containerId);

        return new DockerContainer(containerId, containersCodeAbsolutePath);
    }

    /**
     * 创建执行命令
     *
     * @param dockerClient
     * @return 执行命令ID
     */
    public static String execCreate(DockerClient dockerClient, DockerContainer dockerContainer, String[] cmdArray) {
        ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(dockerContainer.getContainerId())
                .withCmd(cmdArray)
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .exec();
        log.info("创建执行命令：{}", execCreateCmdResponse);
        return execCreateCmdResponse.getId();
    }

    /**
     * 清理容器对应代码目录
     *
     * @param dockerContainer
     */
    public static void clear(DockerContainer dockerContainer) {
        // 删除容器代码目录
        if (FileUtil.del(dockerContainer.getContainersCodeAbsolutePath())) {
            log.info("删除容器代码目录：{}", dockerContainer.getContainersCodeAbsolutePath());
        } else {
            log.error("删除容器代码目录失败：{}", dockerContainer.getContainersCodeAbsolutePath());
        }
        // 新建同名目录
        FileUtil.mkdir(dockerContainer.getContainersCodeAbsolutePath());
    }

    public static void destroy(DockerClient dockerClient, DockerContainer dockerContainer) {
        // 停止容器
        dockerClient.stopContainerCmd(dockerContainer.getContainerId()).exec();
        // 删除容器
        try {
            dockerClient.removeContainerCmd(dockerContainer.getContainerId())
                    .withRemoveVolumes(true)
                    .exec();
        } catch (ConflictException e) {
            log.info("容器删除失败，触发强制删除[ID:{}]", dockerContainer.getContainerId());
            dockerClient.removeContainerCmd(dockerContainer.getContainerId())
                    .withForce(true)
                    .withRemoveVolumes(true)
                    .exec();
        }
        // 删除容器代码目录
        if (FileUtil.del(dockerContainer.getContainersCodeAbsolutePath())) {
            log.info("删除容器代码目录：{}", dockerContainer.getContainersCodeAbsolutePath());
        } else {
            log.error("删除容器代码目录失败：{}", dockerContainer.getContainersCodeAbsolutePath());
        }
    }
}
