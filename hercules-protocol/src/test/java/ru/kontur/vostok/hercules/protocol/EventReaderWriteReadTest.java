package ru.kontur.vostok.hercules.protocol;

import org.junit.Assert;
import org.junit.Test;
import ru.kontur.vostok.hercules.protocol.decoder.EventReader;
import ru.kontur.vostok.hercules.protocol.util.EventBuilder;
import ru.kontur.vostok.hercules.protocol.encoder.EventWriter;
import ru.kontur.vostok.hercules.util.time.TimeUtil;
import ru.kontur.vostok.hercules.uuid.UuidGenerator;

import java.util.Collections;
import java.util.UUID;

public class EventReaderWriteReadTest {

    @Test
    public void shouldWriteReadAllTags() {
        WriteReadPipe<Event> pipe = WriteReadPipe.init(new EventWriter(), EventReader.readAllTags());

        UUID eventId = UuidGenerator.getClientInstance().withTicks(TimeUtil.unixTimeToTicks(123_456_789L));
        EventBuilder builder = new EventBuilder();
        builder.setVersion(1);
        builder.setTimestamp(TimeUtil.unixTimeToTicks(123_456_789L));
        builder.setRandom(eventId);
        builder.setTag("string-tag", Variant.ofString("Abc ЕЁЮ"));
        builder.setTag("flag-array-tag", Variant.ofVector(Vector.ofFlags(new boolean[]{true, true, false})));

        pipe.process(builder.build()).assertEquals(HerculesProtocolAssert::assertEquals);
    }

    @Test
    public void shouldWriteReadNoTags() {
        WriteReadPipe<Event> pipe = WriteReadPipe.init(new EventWriter(), EventReader.readNoTags());

        UUID eventId = UuidGenerator.getClientInstance().withTicks(TimeUtil.unixTimeToTicks(123_456_789L));
        EventBuilder builder = new EventBuilder();
        builder.setVersion(1);
        builder.setTimestamp(TimeUtil.unixTimeToTicks(123_456_789L));
        builder.setRandom(eventId);

        builder.setTag("string-tag", Variant.ofString("Abc ЕЁЮ"));
        builder.setTag("flag-array-tag", Variant.ofVector(Vector.ofFlags(new boolean[]{true, true, false})));

        WriteReadPipe.ProcessedCapture<Event> capture = pipe.process(builder.build());

        Event processed = capture.getProcessed();
        Event original = capture.getOriginal();

        Assert.assertEquals(original.getVersion(), processed.getVersion());
        Assert.assertEquals(original.getTimestamp(), processed.getTimestamp());
        Assert.assertEquals(original.getUuid(), processed.getUuid());

        Assert.assertEquals(0, processed.getPayload().size());

        Assert.assertArrayEquals(original.getBytes(), processed.getBytes());
    }

    @Test
    public void shouldWriteReadOneTag() {
        WriteReadPipe<Event> pipe = WriteReadPipe.init(new EventWriter(), EventReader.readTags(Collections.singleton("string-tag")));

        UUID eventId = UuidGenerator.getClientInstance().withTicks(TimeUtil.unixTimeToTicks(123_456_789L));
        EventBuilder builder = new EventBuilder();
        builder.setVersion(1);
        builder.setTimestamp(TimeUtil.unixTimeToTicks(123_456_789L));
        builder.setRandom(eventId);

        builder.setTag("string-tag", Variant.ofString("Abc ЕЁЮ"));
        builder.setTag("flag-array-tag", Variant.ofVector(Vector.ofFlags(new boolean[]{true, true, false})));

        WriteReadPipe.ProcessedCapture<Event> capture = pipe.process(builder.build());

        Event processed = capture.getProcessed();
        Event original = capture.getOriginal();

        Assert.assertEquals(original.getVersion(), processed.getVersion());
        Assert.assertEquals(original.getTimestamp(), processed.getTimestamp());
        Assert.assertEquals(original.getUuid(), processed.getUuid());

        Assert.assertEquals(1, processed.getPayload().size());
        HerculesProtocolAssert.assertEquals(Variant.ofString("Abc ЕЁЮ"), processed.getPayload().get("string-tag"));

        Assert.assertArrayEquals(original.getBytes(), processed.getBytes());
    }
}
