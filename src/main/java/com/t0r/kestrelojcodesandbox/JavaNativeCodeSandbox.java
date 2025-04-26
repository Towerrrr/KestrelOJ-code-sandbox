package com.t0r.kestrelojcodesandbox;

import cn.hutool.core.io.resource.ResourceUtil;
import com.t0r.kestrelojcodesandbox.model.ExecuteCodeRequest;
import com.t0r.kestrelojcodesandbox.model.ExecuteCodeResponse;
import com.t0r.kestrelojcodesandbox.model.ExecuteMessage;
import com.t0r.kestrelojcodesandbox.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Java 原生代码沙箱
 */
@Component
@Slf4j
public class JavaNativeCodeSandbox extends JavaCodeSandboxTemplate {

    private static final long TIME_OUT = 5000L;
    // 默认安全管理器路径
    private static final String SECURITY_MANAGER_PATH = "E:\\MyProjects\\KestrelOJ\\kestreloj-code-sandbox\\src\\main\\resources\\security";
    // 默认安全管理器类名
    private static final String SECURITY_MANAGER_CLASS_NAME = "DefaultSecurityManager";

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        return super.executeCode(executeCodeRequest);
    }

    /**
     * 启用Java安全管理器执行用户代码，用新进程判断程序是否超时
     *
     * @param userCodeFile
     * @param inputList
     * @return
     * @throws IOException
     */
    @Override
    public List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList) throws IOException {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();

        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String inputArgs : inputList) {
            // todo 包装字符串
            //-Xmx256m 来限制内存使用，防止内存溢出
            String runCmd = String.format("java -Xmx256m \"-Dfile.encoding=UTF-8\" -cp \"%s;%s\" \"-Djava.security.manager=%s\" Main %s",
                    userCodeParentPath, SECURITY_MANAGER_PATH, SECURITY_MANAGER_CLASS_NAME, inputArgs);

            Process runProcess = Runtime.getRuntime().exec(runCmd);
            // 超时处理
            new Thread(() -> {
                try {
                    Thread.sleep(TIME_OUT);
                    try {
                        // 没有抛出异常说明进程已经结束
                        runProcess.exitValue();
                    } catch (IllegalThreadStateException e) {
                        // 抛出异常说明进程未结束，需要强制结束
                        log.info("执行超时，强制结束进程");
                        runProcess.destroy();
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }).start();
            ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(runProcess, "运行");
            log.info("运行结果：{}", executeMessage);
            executeMessageList.add(executeMessage);
        }
        return executeMessageList;
    }

    public static void main(String[] args) {
        JavaNativeCodeSandbox javaNativeCodeSandbox = new JavaNativeCodeSandbox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("1 2", "3 4"));
        String code = ResourceUtil.readStr("testCode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");

        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandbox.executeCode(executeCodeRequest);
        System.out.println(executeCodeResponse);
    }

}
