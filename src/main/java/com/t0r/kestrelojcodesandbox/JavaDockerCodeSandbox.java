package com.t0r.kestrelojcodesandbox;


import cn.hutool.core.date.StopWatch;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.t0r.kestrelojcodesandbox.docker.DockerContainer;
import com.t0r.kestrelojcodesandbox.model.ExecuteCodeRequest;
import com.t0r.kestrelojcodesandbox.model.ExecuteCodeResponse;
import com.t0r.kestrelojcodesandbox.model.ExecuteMessage;
import com.t0r.kestrelojcodesandbox.utils.DockerPoolManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.t0r.kestrelojcodesandbox.enums.JudgeInfoMessageEnum;

import javax.annotation.Resource;
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

    @Resource
    private DockerPoolManager dockerPoolManager;

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
     * todo 目前先专门制定容器代码映射的路径，后续看一下要不要直接把用户的代码直接存到映射的路径
     *
     * @param userCodeFile
     * @param inputList
     * @return
     */
    @Override
    public List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList) {

        // 获取Docker客户端
        DockerClient dockerClient = dockerPoolManager.getDockerClient();

        DockerContainer container = null;
        try {
            container = dockerPoolManager.borrowObject();

            // 复制文件到容器对应目录
            String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
            File sourceDir = new File(userCodeParentPath);
            File destDir = new File(container.getContainersCodeAbsolutePath());

            File[] files = sourceDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    FileUtil.copy(file, new File(destDir, file.getName()), true);
                }
            }
            log.info("复制文件到容器目录：{} -> {}", userCodeParentPath, container.getContainersCodeAbsolutePath());

//            // 使用hutool打印目标文件夹中的文件列表
//            List<File> fileList = Arrays.asList(FileUtil.ls(container.getContainersCodeAbsolutePath()));
//            for (File file : fileList) {
//                log.info("文件夹中的文件: {}", file.getAbsolutePath());
//            }

            // 执行命令并获取结果
            // docker exec sharp_burnell java -cp /code  Main 1 4
            List<ExecuteMessage> executeMessageList = new ArrayList<>();
            for (String inputArgs : inputList) {
                StopWatch stopWatch = new StopWatch();
                ExecuteMessage executeMessage = new ExecuteMessage();
                final String[] message = {null};
                final String[] errorMessage = {null};
                long time = 0L;
                final boolean[] isTimeout = {true};
                final long[] maxMemory = {0L};

                String[] inputArgArray = inputArgs.split(" ");
                String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", "/code", "Main"}, inputArgArray);
                ResultCallback.Adapter<Frame> callback = getFrameAdapter(errorMessage, message, isTimeout);
                String execId = DockerContainer.execCreate(dockerClient, container, cmdArray);

                // 结果回调获取内存占用
                StatsCmd statsCmd = dockerClient.statsCmd(container.getContainerId());
                ResultCallback<Statistics> resultCallback = getStatistics(maxMemory);
                statsCmd.exec(resultCallback);

                try {
                    stopWatch.start();
                    dockerClient.execStartCmd(execId)
                            .exec(callback)
                            .awaitCompletion(TIME_OUT, TimeUnit.MILLISECONDS);
                    stopWatch.stop();
                    time = stopWatch.getLastTaskTimeMillis();
                    TimeUnit.MILLISECONDS.sleep(400);
                    statsCmd.close();
                } catch (InterruptedException e) {
                    log.error("容器执行异常：{}", e.getMessage());
                    throw new RuntimeException(e);
                }

                executeMessage.setJudgeInfoMessageEnum(isTimeout[0] ? JudgeInfoMessageEnum.TIME_EXCEEDED : JudgeInfoMessageEnum.ACCEPTED);
                executeMessage.setMessage(message[0]);
                executeMessage.setErrorMessage(errorMessage[0]);
                executeMessage.setTime(time);
                executeMessage.setMemory(maxMemory[0]);
                executeMessageList.add(executeMessage);
            }

            return executeMessageList;
        } catch (Exception e) {
            try {
                dockerPoolManager.invalidateObject(container); // 销毁异常容器
            } catch (Exception ex) {
                log.error("销毁异常容器异常：{}", ex.getMessage());
                throw new RuntimeException(ex);
            }
            log.error("容器获取异常：{}\n已销毁容器：{}", e.getMessage(), container);
            throw new RuntimeException(e);
        } finally {
            if (container != null) {
                dockerPoolManager.returnObject(container); // 归还容器
            }
        }
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
}
