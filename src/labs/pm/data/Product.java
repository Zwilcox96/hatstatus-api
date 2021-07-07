package labs.pm.data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

/**
 * {@code Product} class represents stuff in the shop
 * @author zackwilcox
 * @version 1.0
 */
public abstract class Product implements Rateable, Serializable {
    private final int id;
    private final String name;
    private final BigDecimal price;
    private final Rating rating;
    public static final BigDecimal DISCOUNT_RATE = BigDecimal.valueOf(0.1);

    Product(int id, String name, BigDecimal price, Rating rating) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.rating = rating;
    }

    Product(int id, String name, BigDecimal price) {
        this(id, name, price, Rating.NOT_RATED);
    }

    /**
     * A constant that defines a discount rate
     * @return a {@link java.math.BigDecimal} value of the discount
     */
    public BigDecimal getDiscount(){
        return price.multiply(DISCOUNT_RATE).setScale(2, RoundingMode.HALF_UP);
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    @Override
    public Rating getRating() {
        return rating;
    }

    public abstract Product applyRating(Rating rating);

    public LocalDate getBestBefore() {
        return LocalDate.now();
    }

    public String getStars(){
        return rating.getStars();
    }

    @Override
    public String toString() {
        return id + ", " + name + ", " + price + ", " + getDiscount() + ", " + getStars() + ", " + getBestBefore();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if ( o instanceof Product) {
            Product product = (Product) o;
            return id == product.id;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
