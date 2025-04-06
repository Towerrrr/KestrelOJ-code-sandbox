package com.t0r.kestrelojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.t0r.kestrelojcodesandbox.model.ExecuteCodeRequest;
import com.t0r.kestrelojcodesandbox.model.ExecuteCodeResponse;
import com.t0r.kestrelojcodesandbox.model.ExecuteMessage;
import com.t0r.kestrelojcodesandbox.model.JudgeInfo;
import com.t0r.kestrelojcodesandbox.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Java 代码沙箱模板
 */
@Slf4j
public abstract class JavaCodeSandboxTemplate implements CodeSandbox {

    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";

    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    private static final long TIME_OUT = 5000L;

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {

        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();

        // 1. 把用户的代码存放到全局目录
        File userCodeFile = savaCodeToFile(code);

        // 2. 编译用户代码
        ExecuteMessage compileFileExecuteMessage = compileFile(userCodeFile);
        System.out.println(compileFileExecuteMessage);

        // 3. 执行用户代码，获取输出
        List<ExecuteMessage> executeMessageList = runFile(userCodeFile, inputList);

        // 4. 处理用户代码的输出
        ExecuteCodeResponse executeCodeResponse = getOutputResponse(executeMessageList);

        // 5. 删除用户代码
        boolean isDeleteSuccess = deleteFile(userCodeFile);
        if (!isDeleteSuccess) {
            log.error("delete file error, userCodeFile: {}", userCodeFile.getAbsolutePath());
        }

        return executeCodeResponse;
    }

    /**
     * 1. 把用户的代码存放到全局目录
     *
     * @param code
     * @return
     */
    public File savaCodeToFile(String code) {
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        // 判断全局代码目录是否存在，不存在则创建
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }
        // 把用户代码隔离存放
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePathName = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        File userCodeFile = FileUtil.writeString(code, userCodePathName, StandardCharsets.UTF_8);
        return userCodeFile;
    }

    /**
     * 2. 编译用户代码
     *
     * @param userCodeFile
     * @return
     */
    public ExecuteMessage compileFile(File userCodeFile) {
        String compileCmd = String.format("javac -encoding UTF-8 %s", userCodeFile.getAbsolutePath());
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(compileProcess, "编译");
            if (executeMessage.getExitCode() != 0) {
                throw new RuntimeException("编译失败");
            }
            return executeMessage;
        } catch (IOException e) {
//            return getErrorResponse(e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 3. 执行用户代码，获取输出
     *
     * @param userCodeFile
     * @param inputList
     * @return
     */
    public List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();

        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String inputArgs : inputList) {
            //-Xmx256m 来限制内存使用，防止内存溢出
            String runCmd = String.format("java -Xmx256m \"-Dfile.encoding=UTF-8\" -cp \"%s\" Main %s",
                    userCodeParentPath, inputArgs);
//            System.out.println(runCmd);
            // java -Xmx256m "-Dfile.encoding=UTF-8" -cp %s;%s "-Djava.security.manager=%s" Main 3 4
            // java -Xmx256m "-Dfile.encoding=UTF-8" -cp E:\MyProjects\KestrelOJ\kestreloj-code-sandbox\tmpCode\aec88875-a24a-4ac0-a0d0-aea48ba12a19;E:\MyProjects\KestrelOJ\kestreloj-code-sandbox\src\main\resources\security "-Djava.security.manager=DefaultSecurityManager" Main 1 2

            try {
                Process runProcess = Runtime.getRuntime().exec(runCmd);
                // 超时处理
                new Thread(() -> {
                    try {
                        Thread.sleep(TIME_OUT);
                        System.out.println("执行超时，强制中止");
                        // todo 要先判断进程是否还在运行，否则会抛出异常
                        runProcess.destroy();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();
                ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(runProcess, "运行");
                System.out.println(executeMessage);
                executeMessageList.add(executeMessage);
            } catch (IOException e) {
                throw new RuntimeException("执行失败", e);
            }
        }
        return executeMessageList;
    }

    /**
     * 4. 处理用户代码的输出
     *
     * @param executeMessageList
     * @return
     */
    public ExecuteCodeResponse getOutputResponse(List<ExecuteMessage> executeMessageList) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        // 取最大值，便于判断是否超时
        long maxTime = 0;
        for (ExecuteMessage executeMessage : executeMessageList) {
            String errorMessage = executeMessage.getErrorMessage();
            if (StrUtil.isNotBlank(errorMessage)) {
                executeCodeResponse.setMessage(errorMessage);
                // 用户提交的代码执行中存在错误
                // todo 枚举
                executeCodeResponse.setStatus(3);
                break;
            }
            outputList.add(executeMessage.getMessage());
            Long time = executeMessage.getTime();
            if (time != null) {
                maxTime = Math.max(maxTime, time);
            }
        }
        // 正常运行完成
        if (outputList.size() == executeMessageList.size()) {
            // todo 枚举
            executeCodeResponse.setStatus(1);
        }
        executeCodeResponse.setOutputList(outputList);

        JudgeInfo judgeInfo = new JudgeInfo();
        // todo 要借助第三方库，暂时不做
//        judgeInfo.setMemory();
        judgeInfo.setTime(maxTime);
        executeCodeResponse.setJudgeInfo(judgeInfo);
        return executeCodeResponse;
    }

    /**
     * 5. 删除用户代码
     *
     * @param userCodeFile
     * @return
     */
    public boolean deleteFile(File userCodeFile) {
        if (userCodeFile.getParentFile().exists()) {
            String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println("删除" + (del ? "成功" : "失败"));
            return del;
        }
        return true;
    }

    /**
     * 获取错误响应
     *
     * @param e
     * @return
     */
    // todo  解决用法
    private ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        // 表示代码沙箱错误
        // todo 枚举
        executeCodeResponse.setStatus(2);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }

}
