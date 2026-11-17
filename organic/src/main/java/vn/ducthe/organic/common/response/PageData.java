package vn.ducthe.organic.common.response;

import java.util.List;
import org.springframework.data.domain.Page;

public record PageData<T>(List<T> items, Pagination pagination) {

    public static <T> PageData<T> from(Page<T> page) {
        return new PageData<>(page.getContent(), new Pagination(
                page.getNumber() + 1, page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.hasNext(), page.hasPrevious()));
    }

    public record Pagination(
            int page, int size, long totalElements, int totalPages, boolean hasNext, boolean hasPrevious) {}
}
