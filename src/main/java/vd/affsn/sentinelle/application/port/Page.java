package vd.affsn.sentinelle.application.port;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Page de résultats, indépendante de tout framework. */
public record Page<T>(List<T> elements, Pagination pagination, long totalElements) {

    public Page {
        Objects.requireNonNull(pagination, "pagination");
        elements = List.copyOf(Objects.requireNonNull(elements, "elements"));
        if (totalElements < elements.size()) {
            throw new IllegalArgumentException("totalElements inférieur au nombre d'éléments de la page");
        }
    }

    public long totalPages() {
        return (totalElements + pagination.taille() - 1) / pagination.taille();
    }

    public <R> Page<R> map(Function<? super T, ? extends R> transformation) {
        return new Page<>(elements.stream().<R>map(transformation).toList(), pagination, totalElements);
    }
}
