package com.campus.card.common;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;



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
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        return Result.fail(ErrCode.PARAM_ERROR, "请求体格式错误");
    }
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException e){
        log.warn("唯一索引冲突：{}",e.getMessage());
        return Result.fail(ErrCode.BIZ_ERROR,"请勿重复提交");
    }

    /**
     * 404：请求路径没有匹配到任何 @RequestMapping。
     * ★ 必须单独处理，否则会掉进下面的兜底 handler，变成「HTTP 200 + 系统繁忙」——
     *   前端看起来像后端炸了，其实只是路径写错了
     *   （最典型的就是 nginx 的 rewrite 把 /api 前缀吃掉，后端收到 /card/flow）。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNotFound(NoResourceFoundException e, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        log.warn("接口不存在：{}", e.getResourcePath());
        return Result.fail(ErrCode.PARAM_ERROR, "接口不存在：/" + e.getResourcePath());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e){
        log.error("系统异常",e);
        return Result.fail(ErrCode.SYSTEM_ERROR,"系统繁忙，请稍后重试");
    }

}
