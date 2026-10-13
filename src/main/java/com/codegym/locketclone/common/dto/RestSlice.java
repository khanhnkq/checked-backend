package com.codegym.locketclone.common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

/**
 * Wrapper cho Slice<T> tương thích hoàn hảo với Spring MVC và Jackson Redis Serializer.
 * Khắc phục triệt để lỗi thiếu default constructor và Unpaged.getOffset() trong SliceImpl của Spring Data.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RestSlice<T> implements Slice<T>, Serializable {
    private final ArrayList<T> content;
    private final int number;
    private final int size;
    private final boolean hasNext;

    @JsonCreator
    public RestSlice(
            @JsonProperty("content") List<T> content,
            @JsonProperty("number") int number,
            @JsonProperty("size") int size,
            @JsonProperty("hasNext") boolean hasNext
    ) {
        this.content = content != null ? new ArrayList<>(content) : new ArrayList<>();
        this.number = number;
        this.size = size;
        this.hasNext = hasNext;
    }

    public static <T> RestSlice<T> from(Slice<T> slice) {
        if (slice instanceof RestSlice<T> restSlice) {
            return restSlice;
        }
        return new RestSlice<>(
                new ArrayList<>(slice.getContent()),
                slice.getNumber(),
                slice.getSize(),
                slice.hasNext()
        );
    }

    @Override public int getNumber() { return number; }
    @Override public int getSize() { return size; }
    @Override public int getNumberOfElements() { return content.size(); }
    @Override public List<T> getContent() { return content; }
    @Override public boolean hasContent() { return !content.isEmpty(); }
    @Override public Sort getSort() { return Sort.unsorted(); }
    @Override public boolean isFirst() { return number == 0; }
    @Override public boolean isLast() { return !hasNext; }
    @Override public boolean hasNext() { return hasNext; }
    @Override public boolean hasPrevious() { return number > 0; }
    @Override public Pageable nextPageable() { return Pageable.unpaged(); }
    @Override public Pageable previousPageable() { return Pageable.unpaged(); }

    @SuppressWarnings("unchecked")
    @Override public <U> Slice<U> map(Function<? super T, ? extends U> converter) {
        List<U> converted = (List<U>) content.stream().map(converter).toList();
        return new RestSlice<>(new ArrayList<>(converted), number, size, hasNext);
    }

    @Override public Iterator<T> iterator() { return content.iterator(); }
}
