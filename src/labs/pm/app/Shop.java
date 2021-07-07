package labs.pm.app;

import labs.pm.data.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * {@code Shop} class represents something
 * @author zackwilcox
 * @version 1.0
 */
public class Shop {
    private final static int MIN_PRODUCT_ID = 101;
    private final static int NUM_PRODUCTS = 5;
    private final static int NUM_CLIENTS = 5;
    private final static int MAX_THREADS = 3;
    private final static Logger logger = Logger.getLogger(Shop.class.getName());

    public static void main(String[] args){
        AtomicInteger clientCount = new AtomicInteger(0);
        ProductManager pm = ProductManager.getInstance();
        Callable<String> client = () -> {
            String clientId = "Client " + clientCount.incrementAndGet();
            String threadName = Thread.currentThread().getName();
            int productId = ThreadLocalRandom.current().nextInt(NUM_PRODUCTS) + MIN_PRODUCT_ID;
            Set<String> supportedLocales = ProductManager.getSupportedLocales();
            String languageTag = supportedLocales.stream().skip(ThreadLocalRandom.current().nextInt(supportedLocales.size())).findFirst().get();
            StringBuilder log = new StringBuilder();
            log.append(clientId).append(" ").append(threadName).append("\n-\tstart of log\t-\n");
            log.append(
                    pm.getDiscounts(languageTag)
                            .entrySet()
                            .stream()
                            .map(entry -> entry.getKey() + "\t" + entry.getValue())
                            .collect(Collectors.joining("\n")));
            Product product = pm.reviewProduct(productId, Rating.FOUR_STAR, "Yet another review from " + clientId);
            log.append((product != null) ? "\nProduct " + productId + " reviewed\n" : "\nProduct " + productId + " not reviewed\n");
            pm.printProductReport(productId, languageTag, clientId);
            log.append(clientId).append(" generated report for ").append(productId).append(" product");
            log.append("\n-\tend of log\t-\n");
            return log.toString();
        };
        List<Callable<String>> clients = Stream.generate(() -> client).limit(NUM_CLIENTS).collect(Collectors.toList());
        ExecutorService executorService = Executors.newFixedThreadPool(MAX_THREADS);
        try {
            List<Future<String>> results = executorService.invokeAll(clients);
            executorService.shutdown();
            results.stream().forEach(result -> { try {
                System.out.println(result.get());
            } catch (InterruptedException | ExecutionException ex) {
                logger.log(Level.SEVERE, "Error retrieving client log", ex);
            }
            });
        } catch (InterruptedException ex) {
            logger.log(Level.SEVERE, "Error invoking clients", ex);
        }
    }
}
