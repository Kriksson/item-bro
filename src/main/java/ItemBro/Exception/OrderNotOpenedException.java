package ItemBro.Exception;

public class OrderNotOpenedException extends RuntimeException {
    public OrderNotOpenedException(String message) {
        super(message);
    }
}
