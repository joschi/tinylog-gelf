package com.github.joschi.tinylog.gelf;

import org.graylog2.gelfclient.GelfMessage;
import org.graylog2.gelfclient.GelfMessageLevel;
import org.graylog2.gelfclient.GelfTransports;
import org.graylog2.gelfclient.transport.GelfTransport;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.pmw.tinylog.Configurator;
import org.pmw.tinylog.Level;
import org.pmw.tinylog.Logger;
import org.pmw.tinylog.writers.ConsoleWriter;
import org.pmw.tinylog.writers.LogEntry;
import org.pmw.tinylog.writers.LogEntryValue;
import org.pmw.tinylog.writers.Writer;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class GelfWriterTest {
    @Test
    public void getRequiredLogEntryValuesIncludesDefaults() {
        final Writer gelfWriter = new GelfWriter("localhost");

        assertThat(gelfWriter.getRequiredLogEntryValues(),
                hasItems(LogEntryValue.DATE, LogEntryValue.LEVEL, LogEntryValue.RENDERED_LOG_ENTRY));
    }

    @Test
    public void getRequiredLogEntryValuesIncludesAdditionalFields() {
        final Writer gelfWriter = new GelfWriter("localhost", 12201, GelfTransports.UDP, "localhost",
                EnumSet.of(LogEntryValue.EXCEPTION, LogEntryValue.PROCESS_ID), Collections.<String, Object>emptyMap(),
                512, 1000, 500, -1, false);

        assertThat(gelfWriter.getRequiredLogEntryValues(), hasItems(
                LogEntryValue.DATE, LogEntryValue.LEVEL, LogEntryValue.RENDERED_LOG_ENTRY,
                LogEntryValue.EXCEPTION, LogEntryValue.PROCESS_ID));
    }

    @Test
    public void testWrite() throws Exception {
        final GelfTransport client = mock(GelfTransport.class);
        final GelfWriter gelfWriter = new GelfWriter("localhost", 12201, GelfTransports.UDP, "myHostName",
                EnumSet.of(LogEntryValue.EXCEPTION, LogEntryValue.PROCESS_ID),
                Collections.<String, Object>singletonMap("staticField", "TEST"), 512, 1000, 500, -1, false);

        @SuppressWarnings("all")
        final RuntimeException exception = new RuntimeException("BOOM!");
        exception.fillInStackTrace();
        final Date now = new Date();
        final ThreadGroup threadGroup = new ThreadGroup("TEST-threadGroup");
        final Thread thread = new Thread(threadGroup, "TEST-thread");
        thread.setPriority(1);

        final LogEntry logEntry = new LogEntry(now, "TEST-processId", thread,
                "TEST-ClassName", "TEST-MethodName", "TEST-FileName", 42, Level.INFO,
                "Test {0}", exception, "Test 123");

        gelfWriter.write(client, logEntry);

        final ArgumentCaptor<GelfMessage> argumentCaptor = ArgumentCaptor.forClass(GelfMessage.class);
        verify(client).send(argumentCaptor.capture());

        final GelfMessage message = argumentCaptor.getValue();
        assertThat(message.getHost(), equalTo("myHostName"));
        assertThat(message.getMessage(), equalTo("Test 123"));
        assertThat(message.getLevel(), equalTo(GelfMessageLevel.INFO));
        assertThat(new Date((long) (message.getTimestamp() * 1000l)), equalTo(now));
        assertThat(message.getFullMessage(), startsWith(message.getMessage()));

        final Map<String, Object> additionalFields = message.getAdditionalFields();
        assertThat(additionalFields.isEmpty(), is(false));
        assertThat((String) additionalFields.get("processId"), equalTo("TEST-processId"));
        assertThat((String) additionalFields.get("threadName"), equalTo("TEST-thread"));
        assertThat((int) additionalFields.get("threadPriority"), equalTo(1));
        assertThat((String) additionalFields.get("threadGroup"), equalTo("TEST-threadGroup"));
        assertThat((String) additionalFields.get("sourceClassName"), equalTo("TEST-ClassName"));
        assertThat((String) additionalFields.get("sourceMethodName"), equalTo("TEST-MethodName"));
        assertThat((String) additionalFields.get("sourceFileName"), equalTo("TEST-FileName"));
        assertThat((int) additionalFields.get("sourceLineNumber"), equalTo(42));
        assertThat((String) additionalFields.get("exceptionMessage"), equalTo("BOOM!"));
        assertThat((String) additionalFields.get("exceptionClass"), equalTo(RuntimeException.class.getCanonicalName()));
        assertThat((String) additionalFields.get("exceptionStackTrace"), startsWith(this.getClass().getCanonicalName()));
    }

    @Test
    public void testFlush() throws Exception {
        new GelfWriter("localhost").flush();
    }

    @Test
    public void testClose() throws Exception {
        final GelfWriter gelfWriter = new GelfWriter();
        Configurator.defaultConfig()
                .writer(gelfWriter)
                .level(Level.INFO)
                .activate();
        gelfWriter.close();
    }

    @Test
    public void testLogging() {
        Configurator.defaultConfig()
                .writer(new GelfWriter("localhost"))
                .addWriter(new ConsoleWriter())
                .level(Level.TRACE)
                .activate();

        @SuppressWarnings("all")
        final RuntimeException exception = new RuntimeException("BOOM!");
        exception.fillInStackTrace();

        Logger.trace("Test");
        Logger.trace(exception, "Test");
        Logger.trace("Test {0}", 1234);
        Logger.debug("Test");
        Logger.debug(exception, "Test");
        Logger.debug("Test {0}", 1234);
        Logger.info("Test");
        Logger.info(exception, "Test");
        Logger.info("Test {0}", 1234);
        Logger.warn("Test");
        Logger.warn(exception, "Test");
        Logger.warn("Test {0}", 1234);
        Logger.error("Test");
        Logger.error(exception, "Test");
        Logger.error("Test {0}", 1234);
    }

    @Test
    public void testLoggingFromProperties() throws IOException {
        Configurator.fromResource("gelf-writer.properties").activate();

        @SuppressWarnings("all")
        final RuntimeException exception = new RuntimeException("BOOM!");
        exception.fillInStackTrace();

        Logger.trace("Test");
        Logger.trace(exception, "Test");
        Logger.trace("Test {0}", 1234);
        Logger.debug("Test");
        Logger.debug(exception, "Test");
        Logger.debug("Test {0}", 1234);
        Logger.info("Test");
        Logger.info(exception, "Test");
        Logger.info("Test {0}", 1234);
        Logger.warn("Test");
        Logger.warn(exception, "Test");
        Logger.warn("Test {0}", 1234);
        Logger.error("Test");
        Logger.error(exception, "Test");
        Logger.error("Test {0}", 1234);
    }
}