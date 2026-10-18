package ItemBro.Exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.io.FileNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(GameNotFountException.class)
    public ResponseEntity<ApiError> handleGameNotFountException(GameNotFountException ex) {
        ApiError apiError = new ApiError(ex.getMessage());
        return new ResponseEntity<>(apiError, HttpStatus.NOT_FOUND);
    }
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiError> handleUserNotFoundException(UserNotFoundException ex) {
        ApiError apiError = new ApiError(ex.getMessage());
        return new ResponseEntity<>(apiError, HttpStatus.NOT_FOUND);
    }
    @ExceptionHandler(UserLimitAdviceException.class)
    public ResponseEntity<ApiError> handleUserLimitAdviceException(UserLimitAdviceException ex) {
        ApiError apiError = new ApiError(ex.getMessage());
        return new ResponseEntity<>(apiError, HttpStatus.CONFLICT);
    }
    @ExceptionHandler(UserAlreadyRegisterException.class)
    public ResponseEntity<ApiError> handleUserAlreadyRegisterException(UserAlreadyRegisterException ex) {
        ApiError apiError = new ApiError(ex.getMessage());
        return new ResponseEntity<>(apiError, HttpStatus.CONFLICT);
    }
    @ExceptionHandler(AdviceNotFoundException.class)
    public ResponseEntity<ApiError> handleAdviceNotFoundException(AdviceNotFoundException ex) {
        ApiError apiError = new ApiError(ex.getMessage());
        return new ResponseEntity<>(apiError, HttpStatus.NOT_FOUND);
    }
    @ExceptionHandler(NoPermissionsException.class)
    public ResponseEntity<ApiError> handleNoPermissionsException(NoPermissionsException ex) {
        ApiError apiError = new ApiError(ex.getMessage());
        return new ResponseEntity<>(apiError, HttpStatus.FORBIDDEN);
    }
    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ApiError> handleInsufficientBalanceException(InsufficientBalanceException ex) {
        ApiError apiError = new ApiError(ex.getMessage());
        return new ResponseEntity<>(apiError, HttpStatus.CONFLICT);
    }
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ApiError> handleOrderNotFoundException(OrderNotFoundException ex) {
        ApiError apiError = new ApiError(ex.getMessage());
        return new ResponseEntity<>(apiError, HttpStatus.NOT_FOUND);
    }
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleObjectOptimisticLockingFailure(ObjectOptimisticLockingFailureException ex) {
        ApiError apiError = new ApiError(ex.getMessage());
        return new ResponseEntity<>(apiError, HttpStatus.CONFLICT);
    }
    @ExceptionHandler(OrderNotOpenedException.class)
    public ResponseEntity<ApiError> handleOrderNotOpenedException(OrderNotOpenedException ex) {
        ApiError apiError = new ApiError(ex.getMessage());
        return new ResponseEntity<>(apiError, HttpStatus.CONFLICT);
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgumentException(IllegalArgumentException ex) {
        ApiError apiError = new ApiError(ex.getMessage());
        return new ResponseEntity<>(apiError, HttpStatus.BAD_REQUEST);
    }
    @ExceptionHandler(FeedbackNotCreatedException.class)
    public ResponseEntity<ApiError> handleFeedbackNotCreatedException(FeedbackNotCreatedException ex) {
        ApiError apiError = new ApiError(ex.getMessage());
        return new ResponseEntity<>(apiError, HttpStatus.CONFLICT);
    }
    @ExceptionHandler(GameAlreadyCreatedException.class)
    public ResponseEntity<ApiError> handleGameAlreadyCreatedException(GameAlreadyCreatedException ex) {
        ApiError apiError = new ApiError(ex.getMessage());
        return new ResponseEntity<>(apiError, HttpStatus.CONFLICT);
    }
    @ExceptionHandler(GameNotOpenedException.class)
    public ResponseEntity<ApiError> handleGameNotOpenedException(GameNotOpenedException ex) {
        ApiError apiError = new ApiError(ex.getMessage());
        return new ResponseEntity<>(apiError, HttpStatus.CONFLICT);
    }
    @ExceptionHandler(GameArchivedException.class)
    public ResponseEntity<ApiError> handleGameArchivedException(GameArchivedException ex) {
        ApiError apiError = new ApiError(ex.getMessage());
        return new ResponseEntity<>(apiError, HttpStatus.CONFLICT);
    }
    @ExceptionHandler(FileNotFoundException.class)
    public ResponseEntity<ApiError> handleFileNotFoundException(FileNotFoundException ex) {
        ApiError apiError = new ApiError(ex.getMessage());
        return new ResponseEntity<>(apiError, HttpStatus.NOT_FOUND);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        ApiError apiError = new ApiError(ex.getBindingResult().getFieldError().getDefaultMessage());
        return new ResponseEntity<>(apiError, HttpStatus.BAD_REQUEST);
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        ApiError apiError = new ApiError("Некорректное тело запроса");
        return new ResponseEntity<>(apiError, HttpStatus.BAD_REQUEST);
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public  ResponseEntity<ApiError> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex) {
        ApiError apiError = new ApiError("Размер изображения - до 5МБ");
        return new ResponseEntity<>(apiError, HttpStatus.PAYLOAD_TOO_LARGE);
    }
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiError> handleMissingServletRequestPartException(MissingServletRequestPartException ex) {
        ApiError apiError = new ApiError(ex.getMessage());
        return new ResponseEntity<>(apiError, HttpStatus.BAD_REQUEST);
    }
    @ExceptionHandler(IllegalMessageRequestException.class)
    public ResponseEntity<ApiError> handleIllegalMessageRequestException(IllegalMessageRequestException ex) {
        ApiError apiError = new ApiError(ex.getMessage());
        return new ResponseEntity<>(apiError, HttpStatus.CONFLICT);
    }
    @ExceptionHandler(OrderNotClosedException.class)
    public ResponseEntity<ApiError> handleOrderNotClosedException(OrderNotClosedException ex) {
        ApiError apiError = new ApiError(ex.getMessage());
        return new ResponseEntity<>(apiError, HttpStatus.CONFLICT);
    }
}
