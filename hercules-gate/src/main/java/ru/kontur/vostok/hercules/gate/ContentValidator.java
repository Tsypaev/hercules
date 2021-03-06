package ru.kontur.vostok.hercules.gate;

import ru.kontur.vostok.hercules.meta.auth.validation.Validation;
import ru.kontur.vostok.hercules.meta.filter.Filter;
import ru.kontur.vostok.hercules.protocol.Event;
import ru.kontur.vostok.hercules.protocol.Variant;

/**
 * @author Gregory Koshelev
 */
public class ContentValidator {
    private final Validation validation;

    public ContentValidator(Validation validation) {
        this.validation = validation;
    }

    public boolean validate(Event event) {
        for (Filter filter : validation.getFilters()) {
            Variant value = event.getPayload().get(filter.getTag());
            if (!filter.getCondition().test(value)) {
                return false;
            }
        }
        return true;
    }
}
