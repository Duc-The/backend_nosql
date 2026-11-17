package vn.ducthe.organic.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import vn.ducthe.organic.common.response.PageData;
import vn.ducthe.organic.dto.response.CatalogResponses.Category;
import vn.ducthe.organic.dto.response.CatalogResponses.Product;
import vn.ducthe.organic.dto.response.CatalogResponses.ProductDetail;

public interface CatalogService {

    List<Category> categories();

    PageData<Product> products(String q, String categorySlug, BigDecimal minPrice, BigDecimal maxPrice,
                                      boolean featured, Shelf shelf, int page, int size);

    enum Shelf { DISCOUNTED, BESTSELLING }

    ProductDetail productDetail(String slug);

    Set<String> visibleCategoryIds();
}
