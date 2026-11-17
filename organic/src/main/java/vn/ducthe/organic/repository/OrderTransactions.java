package vn.ducthe.organic.repository;

import com.mongodb.MongoException;
import java.util.function.Supplier;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import vn.ducthe.organic.exception.ApiError;
import vn.ducthe.organic.exception.ApiException;

@Component
public class OrderTransactions {
    private final TransactionTemplate transactions;

    public OrderTransactions(MongoTemplate mongo) {
        transactions = new TransactionTemplate(new MongoTransactionManager(mongo.getMongoDatabaseFactory()));
    }

    public <T> T execute(Supplier<T> action) {
        for (int attempt = 1; ; attempt++) {
            try {
                return transactions.execute(status -> action.get());
            } catch (RuntimeException exception) {
                MongoException cause = mongoCause(exception);
                if (cause != null && cause.getCode() == 20) {
                    throw new ApiException(ApiError.ORDER_STORAGE_UNAVAILABLE);
                }
                if (cause != null && cause.hasErrorLabel("UnknownTransactionCommitResult")) {
                    throw new ApiException(ApiError.ORDER_RETRY);
                }
                if (cause == null || !cause.hasErrorLabel("TransientTransactionError")) {
                    throw exception;
                }
                if (attempt == 3) {
                    throw new ApiException(ApiError.ORDER_RETRY);
                }
            }
        }
    }

    private static MongoException mongoCause(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof MongoException mongoException) {
                return mongoException;
            }
        }
        return null;
    }
}
