package ItemBro.Exception;

public class UserAlreadyRegisterException extends RuntimeException {
    public UserAlreadyRegisterException(String message) {
        super(message);
    }
}
