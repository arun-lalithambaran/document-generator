package com.neptune.afo.exception;

import com.neptune.afo.model.ErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(NotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  @ResponseBody
  public ErrorResponse notFoundExceptionHandler(NotFoundException ex) {
    return buildErrorResponse(ex.getMessage(), HttpStatus.NOT_FOUND.value());
  }

  @ExceptionHandler(UnAuthorizedException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  @ResponseBody
  public ErrorResponse unAuthorizedExceptionHandler(UnAuthorizedException ex) {
    return buildErrorResponse(ex.getMessage(), HttpStatus.NOT_FOUND.value());
  }

  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  @ResponseBody
  public ErrorResponse allExceptionHandler(Exception ex) {
    return buildErrorResponse(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
  }

  private ErrorResponse buildErrorResponse(String message, int status) {
    return ErrorResponse.builder().message(message).status(status).build();
  }
}
