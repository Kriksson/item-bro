package ItemBro.Exception;

public class OrderNotClosedException extends RuntimeException {
    public OrderNotClosedException(String message) {
        super(message);
    }
}
