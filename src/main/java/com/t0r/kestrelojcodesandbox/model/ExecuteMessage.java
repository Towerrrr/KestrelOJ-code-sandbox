package com.t0r.kestrelojcodesandbox.model;

import lombok.Data;

/**
 * 进程执行消息
 */
@Data
public class ExecuteMessage {

    private Integer exitCode;

    private String message;

    private String errorMessage;

    private Long time;

    private Long memory;
}
