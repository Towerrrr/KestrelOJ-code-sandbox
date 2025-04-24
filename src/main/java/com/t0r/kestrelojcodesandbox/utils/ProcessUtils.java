package com.t0r.kestrelojcodesandbox.utils;

import com.t0r.kestrelojcodesandbox.model.ExecuteMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.StopWatch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 进程工具类
 */
@Slf4j
public class ProcessUtils {

    public static void main(String[] args) {
        String runProcess = "java -version"; // 要执行的命令
        String opName = "run"; // 操作名

        try {
            Process process = Runtime.getRuntime().exec(runProcess);
            ExecuteMessage result = runProcessAndGetMessage(process, opName);
            log.info("执行结果: {}", result);
        } catch (Exception e) {
            log.error("执行过程中出现错误: ", e);
        }
    }

    /**
     * 执行进程并获取信息
     *
     * @param runProcess
     * @param opName
     * @return
     */
    public static ExecuteMessage runProcessAndGetMessage(Process runProcess, String opName) {
        ExecuteMessage executeMessage = new ExecuteMessage();

        try {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            // 等待编译完成，获取返回值
            int exitCode = runProcess.waitFor();
            executeMessage.setExitCode(exitCode);
            // 正常退出
            if (exitCode == 0) {
                System.out.println(opName + "成功");
                // 分批获取进程的正常输出
                gatherOutput(runProcess, executeMessage, false);
                // 分批获取进程的错误输出
                gatherOutput(runProcess, executeMessage, true);
            } else {
                System.out.println(opName + "失败，退出码：" + exitCode);
                // 分批获取进程的正常输出
                gatherOutput(runProcess, executeMessage, false);
                // 分批获取进程的错误输出
                gatherOutput(runProcess, executeMessage, true);
            }
            stopWatch.stop();
            executeMessage.setTime(stopWatch.getLastTaskTimeMillis());
        } catch (InterruptedException | IOException e) {
            log.error("执行进程并获取信息失败", e);
            throw new RuntimeException(e);
        }
        return executeMessage;
    }

    /**
     * 分批获取进程的输出
     *
     * @param runProcess
     * @param executeMessage
     */
    private static void gatherOutput(Process runProcess, ExecuteMessage executeMessage, boolean isError) throws IOException {
        BufferedReader bufferedReader = isError ?
                new BufferedReader(new InputStreamReader(runProcess.getErrorStream())) :
                new BufferedReader(new InputStreamReader(runProcess.getInputStream()));
        List<String> outputStrList = new ArrayList<>();
        // 逐行读取输出
        String outputLine;
        while ((outputLine = bufferedReader.readLine()) != null) {
            outputStrList.add(outputLine);
            System.out.println(outputLine);
        }
        if (isError) {
            executeMessage.setErrorMessage(StringUtils.join(outputStrList, "\n"));
        } else {
            executeMessage.setMessage(StringUtils.join(outputStrList, "\n"));
        }
    }

    // todo 执行交互式进程并获取信息
}
