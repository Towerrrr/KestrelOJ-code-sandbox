package com.t0r.kestrelojcodesandbox.docker;

import cn.hutool.core.io.FileUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.TaskStatusContainerStatus;
import com.github.dockerjava.api.model.Volume;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

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
     * 创建容器
     *
     * @param dockerClient
     * @param image
     * @return 容器ID
     */
    public static DockerContainer create(DockerClient dockerClient, String image) {
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
        return new DockerContainer(createContainerResponse.getId(), containersCodeAbsolutePath);
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
