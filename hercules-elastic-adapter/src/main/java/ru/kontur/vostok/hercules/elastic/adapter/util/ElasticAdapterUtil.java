package ru.kontur.vostok.hercules.elastic.adapter.util;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.kontur.vostok.hercules.protocol.Event;
import ru.kontur.vostok.hercules.protocol.Variant;
import ru.kontur.vostok.hercules.protocol.encoder.EventBuilder;
import ru.kontur.vostok.hercules.util.io.StreamUtil;
import ru.kontur.vostok.hercules.uuid.UuidGenerator;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

public class ElasticAdapterUtil {
    private static final UuidGenerator GENERATOR = UuidGenerator.getClientInstance();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final JsonFactory JSON_FACTORY = new JsonFactory();
    private static final Map<Class<?>, Function<Object, Variant>> TYPE_MAPPER = new HashMap<>();
    private static final Map<Class<?>, Function<Object[], Variant>> ARRAY_TYPE_MAPPER = new HashMap<>();

    static {
        ARRAY_TYPE_MAPPER.put(Integer.class, array -> {
            int[] intArray = new int[array.length];

            for (int index = 0; index < array.length; index++) {
                intArray[index] = (int) array[index];
            }

            return Variant.ofIntegerArray(intArray);
        });
        ARRAY_TYPE_MAPPER.put(Double.class, array -> {
            double[] doubleArray = new double[array.length];

            for (int index = 0; index < array.length; index++) {
                doubleArray[index] = (double) array[index];
            }

            return Variant.ofDoubleArray(doubleArray);
        });
        ARRAY_TYPE_MAPPER.put(Boolean.class, array -> {
            boolean[] boolArray = new boolean[array.length];

            for (int index = 0; index < array.length; index++) {
                boolArray[index] = (boolean) array[index];
            }

            return Variant.ofFlagArray(boolArray);
        });
        ARRAY_TYPE_MAPPER.put(String.class,
                array -> Variant.ofStringArray(Arrays.stream(array).toArray(String[]::new)));


        TYPE_MAPPER.put(Integer.class, object -> Variant.ofInteger((int) object));
        TYPE_MAPPER.put(Double.class, object -> Variant.ofDouble((double) object));
        TYPE_MAPPER.put(Boolean.class, object -> Variant.ofFlag((boolean) object));
        TYPE_MAPPER.put(String.class, object -> Variant.ofString((String) object));
    }

    public static Event createEvent(Map<String, Variant> map) {
        EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.setVersion(1);
        eventBuilder.setEventId(GENERATOR.next());

        map.forEach(eventBuilder::setTag);

        return eventBuilder.build();
    }

    public static <TInputStream extends InputStream> Stream<Event> createEventStream(TInputStream inputStream) throws IOException {
        JsonParser parser = JSON_FACTORY.createParser(inputStream);
        Iterator<Map<String, Object>> iterator = OBJECT_MAPPER.readValues(parser, new TypeReference<Map<String, Object>>() {
        });

        return StreamUtil.asStream(iterator)
                .map(ElasticAdapterUtil::processMap)
                .map(ElasticAdapterUtil::createEvent);
    }

    private static Map<String, Variant> processMap(Map<String, Object> map) {
        Map<String, Variant> result = new HashMap<>();

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() instanceof ArrayList) {
                result.put(entry.getKey(), processArray((ArrayList)entry.getValue()));
            } else {
                result.put(entry.getKey(), processValue(entry.getValue()));
            }
        }

        return result;
    }

    private static Variant processArray(ArrayList list) {
        if (list.size() == 0) {
            return Variant.ofStringArray(new String[0]);
        } else {
            Class<?> aClass = list.get(0).getClass();

            if (!ARRAY_TYPE_MAPPER.containsKey(aClass)) {
                throw new IllegalStateException("Array Class " + aClass.getSimpleName() + " not found");
            }

            return ARRAY_TYPE_MAPPER.get(aClass).apply(list.toArray());
        }
    }

    private static Variant processValue(Object object) {
        Class<?> aClass = object.getClass();

        if (!TYPE_MAPPER.containsKey(aClass)) {
            throw new IllegalStateException("Class " + aClass.getSimpleName() + " not found");
        }

        return TYPE_MAPPER.get(aClass).apply(object);
    }
}
