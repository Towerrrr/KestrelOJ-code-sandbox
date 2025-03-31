package com.t0r.kestrelojcodesandbox.utils;

import com.t0r.kestrelojcodesandbox.model.ExecuteMessage;
import org.springframework.util.StopWatch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * 进程工具类
 */
public class ProcessUtils {

    /**
     * 执行进程并获取信息
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
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(runProcess.getInputStream()));
                StringBuilder compileOutputStringBuilder = new StringBuilder();
                // 逐行读取输出
                String compileOutputLine;
                while ((compileOutputLine = bufferedReader.readLine()) != null) {
                    compileOutputStringBuilder.append(compileOutputLine);
                }
                executeMessage.setMessage(compileOutputStringBuilder.toString());
            } else {
                System.out.println(opName + "失败，退出码：" + exitCode);
                // 分批获取进程的正常输出
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(runProcess.getInputStream()));
                StringBuilder compileOutputStringBuilder = new StringBuilder();
                // 逐行读取输出
                String compileOutputLine;
                while ((compileOutputLine = bufferedReader.readLine()) != null) {
                    compileOutputStringBuilder.append(compileOutputLine);
                }
                // 分批获取进程的错误输出
                BufferedReader errorBufferedReader = new BufferedReader(new InputStreamReader(runProcess.getErrorStream()));
                StringBuilder errorCompileOutput = new StringBuilder();
                // 逐行读取输出
                String errorCompileOutputLine;
                while ((errorCompileOutputLine = errorBufferedReader.readLine()) != null) {
                    errorCompileOutput.append(errorCompileOutputLine);
                }
                executeMessage.setErrorMessage(errorCompileOutput.toString());
            }
            stopWatch.stop();
            executeMessage.setTime(stopWatch.getLastTaskTimeMillis());
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return executeMessage;
    }

    // todo 执行交互式进程并获取信息
}
