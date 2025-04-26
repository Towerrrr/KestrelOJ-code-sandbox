package com.t0r.kestrelojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.FoundWord;
import cn.hutool.dfa.WordTree;
import com.t0r.kestrelojcodesandbox.enums.ExecuteStateEnum;
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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Java 代码沙箱模板
 */
@Slf4j
public abstract class JavaCodeSandboxTemplate implements CodeSandbox {

    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";

    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    // todo 后面最好再重构一下，把字典树单独抽出来
    private static final List<String> backList = Arrays.asList("Files", "exec");

    private static final WordTree WORD_TREE = new WordTree();

    static {
        // 初始化字典树
        WORD_TREE.addWords(backList);
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {

        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();

        // 校验代码中是否含有黑名单命令
        FoundWord foundWord = WORD_TREE.matchWord(code);
        if (foundWord != null) {
            System.out.println("禁止使用: " + foundWord.getFoundWord());
            return getErrorResponse(new RuntimeException("禁止使用" + foundWord.getFoundWord()));
        }

        // 1. 把用户的代码存放到全局目录
        File userCodeFile = savaCodeToFile(code);

        try {
            // 2. 编译用户代码
            compileFile(userCodeFile);

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
        } catch (IOException e) {
            return getErrorResponse(e);
        }
    }

    /**
     * 校验代码中是否含有黑名单命令
     *
     * @param code
     * @return
     */
    // todo 待重构
    public ExecuteCodeResponse containsBlacklistedWord(String code) {
        FoundWord foundWord = WORD_TREE.matchWord(code);
        if (foundWord != null) {
            System.out.println("禁止使用: " + foundWord.getFoundWord());
            return getErrorResponse(new RuntimeException("禁止使用" + foundWord.getFoundWord()));
        }
        return null;
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
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID(); // 用户代码文件夹
        String userCodePathName = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME; // 用户代码文件
        return FileUtil.writeString(code, userCodePathName, StandardCharsets.UTF_8);
    }

    /**
     * 2. 编译用户代码
     *
     * @param userCodeFile
     * @return
     */
    public void compileFile(File userCodeFile) throws IOException {
        // todo 包装这个字符串
        String compileCmd = String.format("javac -encoding UTF-8 %s", userCodeFile.getAbsolutePath());
        Process compileProcess = Runtime.getRuntime().exec(compileCmd);
        ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(compileProcess, "编译");
        if (executeMessage.getExitCode() != 0) {
            throw new RuntimeException("编译失败");
        }
        log.info("编译结果：{}", executeMessage);
    }

    /**
     * 3. 执行用户代码，获取输出
     *
     * @param userCodeFile
     * @param inputList
     * @return
     * @throws IOException
     */
    public abstract List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList) throws IOException;

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
                executeCodeResponse.setStatus(ExecuteStateEnum.FAILED.getValue());
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
            executeCodeResponse.setStatus(ExecuteStateEnum.SUCCESS.getValue());
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
            log.info("删除用户代码{}", del ? "成功" : "失败");
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
    private ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        // 表示代码沙箱错误
        executeCodeResponse.setStatus(ExecuteStateEnum.ERROR.getValue());
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }

}
