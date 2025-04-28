package com.t0r.kestrelojcodesandbox.model;

import com.t0r.kestrelojcodesandbox.enums.JudgeInfoMessageEnum;
import lombok.Data;

/**
 * 进程执行消息
 */
@Data
public class ExecuteMessage {

    private Integer exitCode;

    private JudgeInfoMessageEnum judgeInfoMessageEnum = JudgeInfoMessageEnum.ACCEPTED;

    private String message;

    private String errorMessage;

    private Long time;

    private Long memory;

    @Override
    public String toString() {
        return "\nExecuteMessage {" +
                "\nexitCode=" + exitCode +
                ", \nmessage='" + message + '\'' +
                ", \nerrorMessage='" + errorMessage + '\'' +
                ", \ntime=" + time +
                ", \nmemory=" + memory +
                "\n}";
    }
}
