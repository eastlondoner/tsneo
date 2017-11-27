package eastlondoner.extensions;

import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class GuardedSpliterator<T> implements Spliterator<T> {

    final Supplier<? extends T> generator;

    final Predicate<T> termination;

    final boolean inclusive;


    public GuardedSpliterator(Supplier<? extends T> generator) {
        this.generator = generator;
        this.termination = Objects::isNull;
        this.inclusive = false;
    }

    public GuardedSpliterator(Supplier<? extends T> generator, Predicate<T> termination, boolean inclusive) {
        this.generator = generator;
        this.termination = termination;
        this.inclusive = inclusive;
    }

    @Override
    public boolean tryAdvance(Consumer<? super T> action) {
        T next = generator.get();
        boolean end = termination.test(next);
        if (inclusive || !end) {
            action.accept(next);
        }
        return !end;
    }

    @Override
    public Spliterator<T> trySplit() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public long estimateSize() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int characteristics() {
        return ORDERED & IMMUTABLE & NONNULL;
    }

}