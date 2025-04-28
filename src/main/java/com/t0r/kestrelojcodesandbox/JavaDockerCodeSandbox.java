package com.t0r.kestrelojcodesandbox;


import cn.hutool.core.date.StopWatch;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.t0r.kestrelojcodesandbox.model.ExecuteCodeRequest;
import com.t0r.kestrelojcodesandbox.model.ExecuteCodeResponse;
import com.t0r.kestrelojcodesandbox.model.ExecuteMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.t0r.kestrelojcodesandbox.enums.JudgeInfoMessageEnum;

import java.io.Closeable;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class JavaDockerCodeSandbox extends JavaCodeSandboxTemplate {

    private static final long TIME_OUT = 5000L;

    private static final Boolean FIRST_INIT = false;

    public static void main(String[] args) {
        JavaDockerCodeSandbox javaNativeCodeSandbox = new JavaDockerCodeSandbox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("1 2", "3 4"));
        String code = ResourceUtil.readStr("testCode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");

        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandbox.executeCode(executeCodeRequest);
        System.out.println(executeCodeResponse);
    }

    /**
     * 3. 创建容器，把文件复制到容器内
     *
     * @param userCodeFile
     * @param inputList
     * @return
     */
    @Override
    public List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList) {
        String image = "openjdk:8-alpine";
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();

        // 创建Docker客户端
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();

        // 拉取镜像
        if (FIRST_INIT) {
            pullImage(dockerClient, image);
        }

        // todo 容器池的实现
        // 创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);

        HostConfig hostConfig = new HostConfig();
        hostConfig.withMemory(1024 * 1024 * 1024L);
        hostConfig.withMemorySwap(0L);
        hostConfig.withCpuCount(1L);
        // todo 安全管理配置
        String userDir = System.getProperty("user.dir");
        String seccompConfigPath = "src/main/java/com/t0r/kestrelojcodesandbox/security/seccomp.json";
        String seccompConfigAbsolutePath = userDir + File.separator + seccompConfigPath;
        // todo 先用默认地址
//        hostConfig.withSecurityOpts(Collections.singletonList("seccomp=https://raw.githubusercontent.com/moby/moby/master/profiles/seccomp/default.json"));
        hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/code")));

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
        String createContainerResponseId = createContainerResponse.getId();

        // 启动容器
        StartContainerCmd startContainerCmd = dockerClient.startContainerCmd(createContainerResponseId);
        startContainerCmd.exec();

        // 执行命令并获取结果
        // docker exec sharp_burnell java -cp /code  Main 1 4
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String inputArgs : inputList) {

            StopWatch stopWatch = new StopWatch();

            String[] inputArgArray = inputArgs.split(" ");
            String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", "/code", "Main"}, inputArgArray);
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(createContainerResponseId)
                    .withCmd(cmdArray)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .exec();
            log.info("创建执行命令：{}", execCreateCmdResponse);

            ExecuteMessage executeMessage = new ExecuteMessage();
            final String[] message = {null};
            final String[] errorMessage = {null};
            String execId = execCreateCmdResponse.getId();
            long time = 0L;
            final boolean[] isTimeout = {true};
            ResultCallback.Adapter<Frame> callback = getFrameAdapter(errorMessage, message, isTimeout);

            // 获取占用内存
            // todo 太快了应该是，优化成都能获取到内存
            final long[] maxMemory = {0L};
            StatsCmd statsCmd = dockerClient.statsCmd(createContainerResponseId);
            ResultCallback<Statistics> resultCallback = getStatistics(maxMemory);
            statsCmd.exec(resultCallback);

            try {
                stopWatch.start();
                dockerClient.execStartCmd(execId)
                        .exec(callback)
                        .awaitCompletion(TIME_OUT, TimeUnit.MILLISECONDS);
                stopWatch.stop();
                time = stopWatch.getLastTaskTimeMillis();
                statsCmd.close();
            } catch (InterruptedException e) {
                log.error("执行异常：{}", e.getMessage());
                throw new RuntimeException(e);
            }
            executeMessage.setJudgeInfoMessageEnum(isTimeout[0]? JudgeInfoMessageEnum.TIME_EXCEEDED : JudgeInfoMessageEnum.ACCEPTED);
            executeMessage.setMessage(message[0]);
            executeMessage.setErrorMessage(errorMessage[0]);
            executeMessage.setTime(time);
            executeMessage.setMemory(maxMemory[0]);
            executeMessageList.add(executeMessage);
        }
        return executeMessageList;
    }

    /**
     * 获取执行结果回调
     *
     * @param errorMessage
     * @param message
     * @return
     */
    private static ResultCallback.Adapter<Frame> getFrameAdapter(String[] errorMessage, String[] message, boolean[] isTimeout) {
        return new ResultCallback.Adapter<Frame>() {
            @Override
            public void onNext(Frame frame) {
                if (frame.getStreamType() == StreamType.STDERR) {
                    errorMessage[0] = new String(frame.getPayload());
                    log.error("错误输出：{}", errorMessage[0]);
                } else {
                    message[0] = new String(frame.getPayload());
                    log.info("输出结果：{}", message[0]);
                }
                super.onNext(frame);
            }

            @Override
            public void onComplete() {
                // 超时判断
                isTimeout[0] = false;
                log.info("执行完成");
                super.onComplete();
            }

            @Override
            public void onError(Throwable throwable) {
                log.error("执行异常：{}", throwable.getMessage());
                super.onError(throwable);
            }
        };
    }

    /**
     * 获取容器统计信息回调
     *
     * @param maxMemory
     * @return
     */
    private static ResultCallback<Statistics> getStatistics(long[] maxMemory) {
        return new ResultCallback<Statistics>() {

            @Override
            public void onNext(Statistics statistics) {
                long memory = statistics.getMemoryStats().getUsage();
                log.info("当前内存占用：{}", memory);
                maxMemory[0] = Math.max(maxMemory[0], memory);
            }

            @Override
            public void close() {

            }

            @Override
            public void onStart(Closeable closeable) {

            }

            @Override
            public void onError(Throwable throwable) {

            }

            @Override
            public void onComplete() {

            }

        };
    }

    /**
     * 拉取镜像
     *
     * @param dockerClient
     * @param image
     */
    private static void pullImage(DockerClient dockerClient, String image) {
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
