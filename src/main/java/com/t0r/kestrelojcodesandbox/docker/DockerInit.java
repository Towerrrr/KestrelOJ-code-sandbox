package com.t0r.kestrelojcodesandbox.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.core.DockerClientBuilder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DockerInit {
    public static void main(String[] args) {

        String image = "openjdk:8-alpine";

        // 创建Docker客户端
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();

        // 拉取镜像
        PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
        PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
            @Override
            public void onNext(PullResponseItem item) {
                log.info("下载镜像：{}", item.getStatus());
                super.onNext(item);
            }
        };
        try {
            pullImageCmd
                    // todo 试试不用回调
                    .exec(pullImageResultCallback)
                    .awaitCompletion();
        } catch (InterruptedException e) {
            log.error("拉取镜像异常", e);
            throw new RuntimeException(e);
        }
        log.info("镜像下载完成");
    }
}
