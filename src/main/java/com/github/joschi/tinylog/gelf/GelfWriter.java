package com.github.joschi.tinylog.gelf;

import org.graylog2.gelfclient.GelfConfiguration;
import org.graylog2.gelfclient.GelfMessageBuilder;
import org.graylog2.gelfclient.GelfMessageLevel;
import org.graylog2.gelfclient.GelfTransports;
import org.graylog2.gelfclient.transport.GelfTransport;
import org.pmw.tinylog.Configuration;
import org.pmw.tinylog.Level;
import org.pmw.tinylog.writers.LogEntry;
import org.pmw.tinylog.writers.LogEntryValue;
import org.pmw.tinylog.writers.PropertiesSupport;
import org.pmw.tinylog.writers.Property;
import org.pmw.tinylog.writers.VMShutdownHook;
import org.pmw.tinylog.writers.Writer;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A tinylog {@link org.pmw.tinylog.writers.Writer} writing log messages to a GELF-compatible server like
 * <a href="http://www.graylog2.org/">Graylog2</a>.
 */
@PropertiesSupport(
        name = "gelf",
        properties = {
                @Property(name = "server", type = String.class),
                @Property(name = "port", type = int.class, optional = true),
                @Property(name = "transport", type = String.class, optional = true),
                @Property(name = "hostname", type = String.class, optional = true),
                @Property(name = "additionalLogEntryValues", type = String[].class, optional = true),
                @Property(name = "staticFields", type = String[].class, optional = true)
        }
)
public final class GelfWriter implements Writer {
    private static final String FIELD_SEPARATOR = ":";
    private static final EnumSet<LogEntryValue> BASIC_LOG_ENTRY_VALUES = EnumSet.of(
            LogEntryValue.DATE,
            LogEntryValue.LEVEL,
            LogEntryValue.RENDERED_LOG_ENTRY
    );

    private final String server;
    private final int port;
    private final GelfTransports transport;
    private final String hostname;
    private final Set<LogEntryValue> requiredLogEntryValues;
    private final Map<String, Object> staticFields;
    private final int queueSize;
    private final int connectTimeout;
    private final int reconnectDelay;
    private final int sendBufferSize;
    private final boolean tcpNoDelay;

    private GelfTransport client;

    /**
     * Construct a new GelfWriter instance.
     *
     * @param server                 the hostname of the GELF-compatible server
     * @param port                   the port of the GELF-compatible server
     * @param transport              the transport protocol to use
     * @param hostname               the hostname of the application
     * @param requiredLogEntryValues additional information for log messages, see {@link LogEntryValue}
     * @param staticFields           additional static fields for the GELF messages
     * @param queueSize              the size of the internal queue the GELF client is using
     * @param connectTimeout         the connection timeout for TCP connections in milliseconds
     * @param reconnectDelay         the time to wait between reconnects in milliseconds
     * @param sendBufferSize         the size of the socket send buffer in bytes; a value of {@code -1}
     *                               deactivates the socket send buffer.
     * @param tcpNoDelay             {@code true} if Nagle's algorithm should used for TCP connections,
     *                               {@code false} otherwise
     */
    public GelfWriter(final String server,
                      final int port,
                      final GelfTransports transport,
                      final String hostname,
                      final Set<LogEntryValue> requiredLogEntryValues,
                      final Map<String, Object> staticFields,
                      final int queueSize,
                      final int connectTimeout,
                      final int reconnectDelay,
                      final int sendBufferSize,
                      final boolean tcpNoDelay) {
        this.server = server;
        this.port = port;
        this.transport = transport;
        this.hostname = buildHostName(hostname);
        this.requiredLogEntryValues = buildRequiredLogEntryValues(requiredLogEntryValues);
        this.staticFields = staticFields;
        this.queueSize = queueSize;
        this.connectTimeout = connectTimeout;
        this.reconnectDelay = reconnectDelay;
        this.sendBufferSize = sendBufferSize;
        this.tcpNoDelay = tcpNoDelay;
    }

    /**
     * Construct a new GelfWriter instance.
     *
     * @param server                 the hostname of the GELF-compatible server
     * @param port                   the port of the GELF-compatible server
     * @param transport              the transport protocol to use
     * @param hostname               the hostname of the application
     * @param requiredLogEntryValues additional information for log messages, see {@link LogEntryValue}
     * @param staticFields           additional static fields for the GELF messages
     */
    public GelfWriter(final String server,
                      final int port,
                      final GelfTransports transport,
                      final String hostname,
                      final Set<LogEntryValue> requiredLogEntryValues,
                      final Map<String, Object> staticFields) {
        this(server, port, transport, hostname, requiredLogEntryValues, staticFields, 512, 1000, 500, -1, false);
    }

    /**
     * Construct a new GelfWriter instance which logs to {@code localhost} on the default port ({@code 12201/udp})
     * using an auto-detected local hostname.
     */
    public GelfWriter() {
        this("localhost", 12201);
    }

    /**
     * Construct a new GelfWriter instance.
     *
     * @param server the hostname of the GELF-compatible server
     */
    public GelfWriter(final String server) {
        this(server, 12201);
    }

    /**
     * Construct a new GelfWriter instance.
     *
     * @param server the hostname of the GELF-compatible server
     * @param port   the port of the GELF-compatible server
     */
    public GelfWriter(final String server, final int port) {
        this(server, port, GelfTransports.UDP, null, EnumSet.noneOf(LogEntryValue.class),
                Collections.<String, Object>emptyMap(), 512, 1000, 500, -1, false);
    }

    /**
     * Construct a new GelfWriter instance.
     *
     * @param server   the hostname of the GELF-compatible server
     * @param port     the port of the GELF-compatible server
     * @param hostname the hostname of the application
     */
    public GelfWriter(final String server, final int port, final String hostname) {
        this(server, port, GelfTransports.UDP, hostname, EnumSet.noneOf(LogEntryValue.class),
                Collections.<String, Object>emptyMap(), 512, 1000, 500, -1, false);
    }

    /**
     * Construct a new GelfWriter instance.
     *
     * @param server                   the hostname of the GELF-compatible server
     * @param port                     the port of the GELF-compatible server
     * @param transport                the transport protocol to use
     * @param hostname                 the hostname of the application
     * @param additionalLogEntryValues additional information for log messages, see {@link LogEntryValue}
     * @param staticFields             a list of additional static fields for the GELF messages (key-value-delimiter
     *                                 is ':')
     */
    public GelfWriter(final String server,
                      final int port,
                      final String transport,
                      final String hostname,
                      final String[] additionalLogEntryValues,
                      final String[] staticFields) {
        this(server, port, GelfTransports.valueOf(transport), hostname,
                buildLogEntryValuesFromString(additionalLogEntryValues), buildStaticFields(staticFields),
                512, 1000, 500, -1, false);
    }

    /**
     * Construct a new GelfWriter instance.
     *
     * @param server                   the hostname of the GELF-compatible server
     * @param port                     the port of the GELF-compatible server
     * @param transport                the transport protocol to use
     * @param hostname                 the hostname of the application
     * @param additionalLogEntryValues additional information for log messages, see {@link LogEntryValue}
     * @param staticFields             a list of additional static fields for the GELF messages (key-value-delimiter
     *                                 is ':')
     * @param queueSize                the size of the internal queue the GELF client is using
     * @param connectTimeout           the connection timeout for TCP connections in milliseconds
     * @param reconnectDelay           the time to wait between reconnects in milliseconds
     * @param sendBufferSize           the size of the socket send buffer in bytes; a value of {@code -1}
     *                                 deactivates the socket send buffer.
     * @param tcpNoDelay               {@code true} if Nagle's algorithm should used for TCP connections,
     *                                 {@code false} otherwise
     */
    public GelfWriter(final String server,
                      final int port,
                      final String transport,
                      final String hostname,
                      final String[] additionalLogEntryValues,
                      final String[] staticFields,
                      final int queueSize,
                      final int connectTimeout,
                      final int reconnectDelay,
                      final int sendBufferSize,
                      final boolean tcpNoDelay) {
        this(server, port, GelfTransports.valueOf(transport), hostname,
                buildLogEntryValuesFromString(additionalLogEntryValues), buildStaticFields(staticFields),
                queueSize, connectTimeout, reconnectDelay, sendBufferSize, tcpNoDelay);
    }

    private static EnumSet<LogEntryValue> buildLogEntryValuesFromString(String... logEntryValues) {
        final EnumSet<LogEntryValue> result = EnumSet.noneOf(LogEntryValue.class);

        for (String logEntryValue : logEntryValues) {
            result.add(LogEntryValue.valueOf(logEntryValue));
        }

        return result;
    }

    private static EnumSet<LogEntryValue> buildRequiredLogEntryValues(Set<LogEntryValue> additionalValues) {
        final EnumSet<LogEntryValue> result = EnumSet.copyOf(additionalValues);
        result.addAll(BASIC_LOG_ENTRY_VALUES);
        return result;
    }

    private static Map<String, Object> buildStaticFields(String[] staticFields) {
        if (null == staticFields) {
            return Collections.emptyMap();
        }

        final Map<String, Object> result = new HashMap<>(staticFields.length);
        for (String staticField : staticFields) {
            final int firstSeparatorPosition = staticField.indexOf(FIELD_SEPARATOR);
            final String key = staticField.substring(0, firstSeparatorPosition);
            final String value = staticField.substring(firstSeparatorPosition + FIELD_SEPARATOR.length());
            result.put(key, value);
        }

        return result;
    }

    private String buildHostName(final String hostname) {
        if (null == hostname || hostname.isEmpty()) {
            try {
                return InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                return "localhost";
            }
        }

        return hostname;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<LogEntryValue> getRequiredLogEntryValues() {
        return requiredLogEntryValues;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(Configuration configuration) throws Exception {
        final InetSocketAddress remoteAddress = new InetSocketAddress(server, port);
        final GelfConfiguration gelfConfiguration = new GelfConfiguration(remoteAddress)
                .transport(transport)
                .queueSize(queueSize)
                .connectTimeout(connectTimeout)
                .reconnectDelay(reconnectDelay)
                .sendBufferSize(sendBufferSize)
                .tcpNoDelay(tcpNoDelay);

        client = GelfTransports.create(gelfConfiguration);

        VMShutdownHook.register(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(final LogEntry logEntry) throws Exception {
        write(client, logEntry);
    }

    void write(final GelfTransport gelfClient, final LogEntry logEntry) throws Exception {
        final GelfMessageBuilder messageBuilder = new GelfMessageBuilder(logEntry.getRenderedLogEntry(), hostname)
                .timestamp(logEntry.getDate().getTime() / 1000d)
                .level(toGelfMessageLevel(logEntry.getLevel()))
                .additionalFields(staticFields);

        final String processId = logEntry.getProcessId();
        if (null != processId) {
            messageBuilder.additionalField("processId", processId);
        }

        final Thread thread = logEntry.getThread();
        if (null != thread) {
            messageBuilder.additionalField("threadName", thread.getName());
            messageBuilder.additionalField("threadGroup", thread.getThreadGroup().getName());
            messageBuilder.additionalField("threadPriority", thread.getPriority());
        }

        final String className = logEntry.getClassName();
        if (null != className) {
            messageBuilder.additionalField("sourceClassName", className);
        }

        final String methodName = logEntry.getMethodName();
        if (null != methodName) {
            messageBuilder.additionalField("sourceMethodName", methodName);
        }

        final String fileName = logEntry.getFilename();
        if (null != fileName) {
            messageBuilder.additionalField("sourceFileName", fileName);
        }

        final int lineNumber = logEntry.getLineNumber();
        if (lineNumber != -1) {
            messageBuilder.additionalField("sourceLineNumber", lineNumber);
        }

        @SuppressWarnings("all")
        final Throwable throwable = logEntry.getException();
        if (null != throwable) {
            final StringBuilder stackTraceBuilder = new StringBuilder();
            for (StackTraceElement stackTraceElement : throwable.getStackTrace()) {
                new Formatter(stackTraceBuilder)
                        .format("%s.%s(%s:%d)%n",
                                stackTraceElement.getClassName(), stackTraceElement.getMethodName(),
                                stackTraceElement.getFileName(), stackTraceElement.getLineNumber());
            }

            messageBuilder.additionalField("exceptionClass", throwable.getClass().getCanonicalName());
            messageBuilder.additionalField("exceptionMessage", throwable.getMessage());
            messageBuilder.additionalField("exceptionStackTrace", stackTraceBuilder.toString());
            messageBuilder.fullMessage(logEntry.getRenderedLogEntry() + "\n\n" + stackTraceBuilder.toString());
        }

        gelfClient.send(messageBuilder.build());
    }

    private GelfMessageLevel toGelfMessageLevel(final Level level) {
        switch (level) {
            case TRACE:
            case DEBUG:
                return GelfMessageLevel.DEBUG;
            case INFO:
                return GelfMessageLevel.INFO;
            case WARNING:
                return GelfMessageLevel.WARNING;
            case ERROR:
                return GelfMessageLevel.ERROR;
            default:
                throw new IllegalArgumentException("Invalid log level " + level);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void flush() throws Exception {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws Exception {
        VMShutdownHook.unregister(this);
        if (client != null) {
            client.stop();
        }
    }
}
