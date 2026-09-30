package com.campus.card.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;



/**
 * @Description
 * @Author u
 * @Date 2026/9/30
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler{
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e){
        log.warn("业务异常：{}",e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e){
        e.getBindingResult().getFieldErrors().forEach(fe ->
                log.warn("参数校验失败 field={} 用户传了={} 提示={}",
                        fe.getField(), fe.getRejectedValue(), fe.getDefaultMessage()));

        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("参数错误");
        return Result.fail(ErrCode.PARAM_ERROR,msg);
    }
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException e){
        log.warn("唯一索引冲突：{}",e.getMessage());
        return Result.fail(ErrCode.BIZ_ERROR,"请勿重复提交");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e){
        log.error("系统异常",e);
        return Result.fail(ErrCode.SYSTEM_ERROR,"系统繁忙，请稍后重试");
    }

}
