package ItemBro.Exception;

public class GameNotOpenedException extends RuntimeException {
    public GameNotOpenedException(String message) {
        super(message);
    }
}
