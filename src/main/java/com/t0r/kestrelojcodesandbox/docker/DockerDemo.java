package com.t0r.kestrelojcodesandbox.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.core.DockerClientBuilder;

import java.util.List;

public class DockerDemo {

    public static void main(String[] args) throws InterruptedException {
        // 创建Docker客户端
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
//        build.pingCmd().exec();
        // 拉取镜像
        String image = "nginx:latest";
//        PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
//        PullImageResultCallback pullImageResultCallback = new PullImageResultCallback(){
//            @Override
//            public void onNext(PullResponseItem item) {
//                System.out.println("下载镜像：" + item.getStatus());
//                super.onNext(item);
//            }
//        };
//        pullImageCmd
//                .exec(pullImageResultCallback)
//                .awaitCompletion();
//        System.out.println("镜像下载完成");
        // 创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        CreateContainerResponse createContainerResponse = containerCmd
                .withCmd("echo", "Hello Docker!")
                .exec();
        System.out.println(createContainerResponse.toString());
        String createContainerResponseId = createContainerResponse.getId();
        // 查看容器状态
        ListContainersCmd listContainersCmd = dockerClient.listContainersCmd();
        List<Container> containerList = listContainersCmd.withShowAll(true).exec();
        for (Container container : containerList) {
            System.out.println(container.toString());
        }
        // 启动容器
        StartContainerCmd startContainerCmd = dockerClient.startContainerCmd(createContainerResponseId);
        startContainerCmd.exec();
        // 查看日志
        ResultCallback.Adapter<Frame> callback = new ResultCallback.Adapter<Frame>() {
            @Override
            public void onNext(Frame frame) {
                System.out.println("------------日志------------");
                System.out.println(new String(frame.getPayload()));
            }

        };

        dockerClient.logContainerCmd(createContainerResponseId)
                .withStdOut(true)
                .withStdErr(true)
                .exec(callback)
                // 阻塞线程等待容器退出
                .awaitCompletion();

        // 删除容器
        dockerClient.removeContainerCmd(createContainerResponseId).withForce(true).exec();

        // 删除镜像
        // todo 注意
        dockerClient.removeImageCmd(image).exec();
    }
}
