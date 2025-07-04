package org.forest.chatollama.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.forest.chatollama.model.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import javax.validation.ConstraintViolationException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {


    @Value("${spring.servlet.multipart.max-request-size}")
    private String maxFileSize;

    //用户post输入参数实体校验异常捕获
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public Result<String> handler(MethodArgumentNotValidException e) {
        BindingResult result = e.getBindingResult();
        ObjectError objectError = result.getAllErrors().stream().findFirst().get();
        return Result.fail(objectError.getDefaultMessage());
    }

    //用户输入自定义抛出异常
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(value = IllegalArgumentException.class)
    public Result<String> handler(IllegalArgumentException e) {
        return Result.fail(e.getMessage());
    }

    //用户get输入抛出异常
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(value = {IllegalStateException.class, ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class,
            jakarta.validation.ConstraintViolationException.class})
    public Result<String> handler(Exception e) {
        return Result.fail("参数有误："+ e.getMessage());
    }

    //用户上传文件大小超出限制
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(value = MaxUploadSizeExceededException.class)
    public Result<String> handler(MaxUploadSizeExceededException e) {
        return Result.fail("文件请不要超过" + maxFileSize);
    }


    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(value = RuntimeException.class)
    public Result<String> handler(RuntimeException e) {
        log.error("程序异常", e);
        return Result.fail(HttpStatus.INTERNAL_SERVER_ERROR.value(),"服务器出错,请联系管理员", null);
    }

}
