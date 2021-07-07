package labs.pm.data;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ProductManager {
    private static Logger log = Logger.getLogger(ProductManager.class.getName());
    private Map<Product, List<Review>> products = new HashMap<>();
    private static final Map<String, ResourceFormatter> formatters =
            Map.of("en-GB", new ResourceFormatter(Locale.UK),
                    "en-US", new ResourceFormatter(Locale.US),
                    "fr-FR", new ResourceFormatter(Locale.FRANCE),
                    "ru-RU", new ResourceFormatter(new Locale("ru", "RU")),
                    "zh-CN", new ResourceFormatter(Locale.CHINA));
    private final ResourceBundle config = ResourceBundle.getBundle("labs.pm.data.config");
    private final MessageFormat reviewFormat = new MessageFormat(config.getString("review.data.format"));
    private final MessageFormat productFormat = new MessageFormat(config.getString("product.data.format"));

    private final Path reportsFolder = Path.of(config.getString("reports.folder"));
    private final Path dataFolder = Path.of(config.getString("data.folder"));
    private final Path tempFolder = Path.of(config.getString("temp.folder"));
    private static final ProductManager pm = new ProductManager();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock writeLock = lock.writeLock();
    private final Lock readLock = lock.readLock();

    private ProductManager() {
        loadAllData();
    }

    public static ProductManager getInstance(){
        return pm;
    }

    public static Set<String> getSupportedLocales() {
        return formatters.keySet();
    }

//    public void changeLocale(String languageTag) {
//        formatter = formatters.getOrDefault(languageTag, formatters.get("en-GB"));
//    }

    public Product parseProduct(String text){
        Product product = null;
        try {
            Object[] values = productFormat.parse(text);
            int id = Integer.parseInt((String) values[1]);
            String name = (String) values[2];
            BigDecimal price = BigDecimal.valueOf(Double.parseDouble((String) values[3]));
            Rating rating = Rateable.convert(Integer.parseInt((String) values[4]));
            switch ((String) values[0]) {
                case "D":
                    product = new Drink(id, name, price, rating);
                    break;
                case "F":
                    LocalDate bestBefore = LocalDate.parse((String) values[5]);
                    product = new Food(id, name, price, rating, bestBefore);
            }
        } catch (ParseException | NumberFormatException | DateTimeParseException e) {
            log.log(Level.WARNING,"Error parsing product: " + text);
        }
        return product;
    }

    public Product createProduct(int id, String name, BigDecimal price, Rating rating, LocalDate bestBefore) {
        Product product = null;
        try{
            writeLock.lock();
            product = new Food(id, name, price, rating, bestBefore);
            products.putIfAbsent(product, new ArrayList<>());
        } catch (Exception e){
            log.info("Error adding product {0}"+ e.getMessage());
            return null;
        } finally {
            writeLock.unlock();
        }

        return product;
    }

    public Product createProduct(int id, String name, BigDecimal price, Rating rating) {
        Product product = null;
        try{
            writeLock.lock();
            product = new Drink(id, name, price, rating);
            products.putIfAbsent(product, new ArrayList<>());
        } catch (Exception e){
            log.info("Error adding product {0}"+ e.getMessage());
            return null;
        } finally {
            writeLock.unlock();
        }

        return product;
    }

    public Product findProduct(int id) throws ProductManagerException {
        try {
            readLock.lock();
            return products.keySet().stream()
                    .filter(product -> product.getId() == id)
                    .findFirst().orElseThrow(() ->new ProductManagerException("Product with id " + id + "does not exist"));
        }finally {
            readLock.unlock();
        }
    }

    public void printProductReport(int id, String languageTag, String client) {
        try {
            readLock.lock();
            Product product = findProduct(id);
            printProductReport(product, languageTag, client);
        }catch (ProductManagerException | IOException e){
            log.log(Level.INFO, e.getMessage());
        }finally {
            readLock.unlock();
        }
    }

    public Review parseReview(String text){
        Review review = null;
        try {
            Object[] values = reviewFormat.parse(text);
            review = new Review(Rateable.convert(Integer.parseInt((String) values[0])), (String) values[1]);
        } catch (ParseException | NumberFormatException e) {
            log.log(Level.WARNING,"Error parsing review: " + text);
        }
        return review;
    }

    public Product reviewProduct(int id, Rating rating, String comments) {
        try {
            writeLock.lock();
            return reviewProduct(findProduct(id), rating, comments);
        } catch (ProductManagerException e) {
            log.log(Level.INFO, e.getMessage());
        } finally {
            writeLock.unlock();
        }
        return null;
    }

    private Product reviewProduct(Product product, Rating rating, String comments) {
        List<Review> reviews = products.get(product);
        reviews.add(new Review(rating, comments));
        product = product.applyRating(
                Rateable.convert(
                        (int) Math.round(
                                reviews.stream()
                                        .mapToInt(review -> review.getRating().ordinal())
                                        .average()
                                        .orElse(0))));
        products.remove(product);
        products.put(product, reviews);
        return product;
    }

    private void printProductReport(Product product, String languageTag, String client) throws IOException {
        ResourceFormatter formatter
                = formatters.getOrDefault(languageTag, formatters.get("en-GB"));
        List<Review> reviews = products.get(product);
        Collections.sort(reviews);
        Files.createDirectories(reportsFolder);
        Path productFile = reportsFolder.resolve(MessageFormat.format(config.getString("report.file"), product.getId(), client));
        // Lab suggests "UTF-8" as a string. However, there is a StandardCharsets class that has several CharSets registered. Saver to use that.
        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(Files.newOutputStream(productFile, StandardOpenOption.CREATE), StandardCharsets.UTF_8))) {
            out.append(formatter.formatProduct(product)).append(System.lineSeparator());
            if (reviews.isEmpty()) {
                out.append(formatter.getString("no.reviews"));
            } else {
                out.append(reviews.stream()
                        .map(review -> formatter.formatReview(review) + System.lineSeparator())
                        .collect(Collectors.joining()));
            }
        }
    }

    public void printProducts(Predicate<Product> filter, Comparator<Product> sorter, String languageTag) {
        try {
            readLock.lock();
            ResourceFormatter formatter = formatters.getOrDefault(languageTag, formatters.get("en-GB"));
            StringBuilder txt = new StringBuilder();
            products.keySet()
                    .stream()
                    .sorted(sorter)
                    .filter(filter)
                    .forEach(product -> {
                        txt.append(formatter.formatProduct(product)).append('\n');
                    });
            System.out.println(txt);
        } finally {
            readLock.unlock();
        }

    }

    public Map<String, String> getDiscounts(String languageTag){
        ResourceFormatter formatter = formatters.getOrDefault(languageTag, formatters.get("en-GB"));
        try {
            readLock.lock();
            return products.keySet()
                    .stream()
                    .collect(
                            Collectors.groupingBy(
                                    product -> product.getRating().getStars(),
                                    Collectors.collectingAndThen(
                                            Collectors.summingDouble(
                                                    product -> product.getDiscount().doubleValue()),
                                            discount -> formatter.moneyFormat.format(discount))));
        } finally {
            readLock.unlock();
        }

    }

    private List<Review> loadReviews(Product product){
        List<Review> reviews = null;
        Path file = dataFolder.resolve(MessageFormat.format(config.getString("reviews.data.file"), product.getId()));
        if (Files.notExists(file)) {
            reviews = new ArrayList<>();
        } else {
            try {
                //  Alternative for StandardCharsets.UTF_8: Charset.forName("UTF-8"). However, again this relies on a hardcoded string.
                reviews = Files.lines(file, StandardCharsets.UTF_8)
                        .map(this::parseReview)
                        .filter(review -> review != null)
                        .collect(Collectors.toList());
            } catch (IOException ex) {
                log.log(Level.WARNING, "Error loading reviews " + ex.getMessage());
            }
        }
        return reviews;
    }

    private void loadAllData() {
        try {
            products = Files.list(dataFolder)
                    .filter(file -> file.getFileName().toString().startsWith("product"))
                    .map(file -> loadProduct(file))
                    .filter(product -> product != null)
                    .collect(Collectors.toMap(product -> product,
                            product -> loadReviews(product))
                    );
        } catch (IOException ex) {
            log.log(Level.SEVERE, "Error loading data " + ex.getMessage(), ex);

        }
    }

    private void dumpData() {
        try {
            // You could also use Files.createDirectories, that also creates all parent-folders that does not exist yet.
            // Then the check on existence would not be necessary.
            if (Files.notExists(tempFolder)) {
                Files.createDirectories(tempFolder);
            }
            Path tempFile = tempFolder.resolve(MessageFormat.format(config.getString("temp.file"), Instant.now()));
            try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(tempFile, StandardOpenOption.CREATE))) {
                out.writeObject(products);
                //products = new HashMap<>();
            }

        } catch (IOException ex) {
            log.log(Level.SEVERE, "Error dumping data " + ex.getMessage(), ex);

        }
    }

    @SuppressWarnings("unchecked")
    private void restoreData() {
        try {
            Path tempFile = Files.list(tempFolder)
                    .filter(path -> path.getFileName().toString().endsWith(".tmp")).findFirst().orElseThrow();
            try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(tempFile, StandardOpenOption.DELETE_ON_CLOSE))) {
                System.out.println("Read projects from " + tempFile.getFileName());
                products = (HashMap) in.readObject();
            }
        } catch (Exception ex) {
            log.log(Level.SEVERE, "Error reading data " + ex.getMessage(), ex);

        }

    }

    private Product loadProduct(Path file){
        Product product = null;
        if (Files.exists(file)) {
            try {
                //  Alternative for StandardCharsets.UTF_8: Charset.forName("UTF-8"). However, again this relies on a hardcoded string.
                product = parseProduct(
                        Files.lines(dataFolder.resolve(file), StandardCharsets.UTF_8).findFirst().orElseThrow());
            } catch (IOException ex) {
                log.log(Level.WARNING, "Error loading product " + ex.getMessage());
            }

        }
        return product;
    }
    private static class ResourceFormatter {
        private Locale locale;
        private ResourceBundle resources;
        private DateTimeFormatter dateFormat;
        private NumberFormat moneyFormat;

        public ResourceFormatter(Locale locale) {
            this.locale = locale;
            resources = ResourceBundle.getBundle("labs.pm.data.resources", locale);
            dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).localizedBy(locale);
            moneyFormat = NumberFormat.getCurrencyInstance(locale);
        }

        private String formatProduct(Product product) {
            return MessageFormat.format(resources.getString("product"),
                    product.getName(), moneyFormat.format(product.getPrice()),
                    product.getRating().getStars(), dateFormat.format(product.getBestBefore()));
        }

        private String formatReview(Review review) {
            return MessageFormat.format(resources.getString("review"),
                    review.getRating().getStars(), review.getComments());
        }

        private String getString(String key) {
            return resources.getString(key);
        }
    }
}
