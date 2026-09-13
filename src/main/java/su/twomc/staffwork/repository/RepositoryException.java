package su.twomc.staffwork.repository;

/** Необрабатываемая (в месте вызова) ошибка хранилища — сообщение никогда не должно содержать пароли/токены. */
public class RepositoryException extends RuntimeException {

    public RepositoryException(String message) {
        super(message);
    }

    public RepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
