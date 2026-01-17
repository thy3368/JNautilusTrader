package com.tanggo.fund.jnautilustrader.adapter.event_repo;

import com.tanggo.fund.jnautilustrader.core.entity.Event;
import com.tanggo.fund.jnautilustrader.core.entity.EventRepo;
import org.apache.avro.Schema;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.avro.AvroParquetReader;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 事件仓储实现 - 通过ParquetFile读写事件
 */

public class ParquetFileQueueEventRepo<T> implements EventRepo<T>, Closeable {

    private final BlockingQueue<Event<T>> eventQueue;
    private final String parquetFilePath;
    private final ParquetWriter<Event<T>> parquetWriter;
    private ParquetReader<Event<T>> parquetReader;
    private volatile boolean readerInitialized = false;

    public ParquetFileQueueEventRepo() {
        this.eventQueue = new LinkedBlockingQueue<>();
        this.parquetFilePath = "events_" + System.currentTimeMillis() + ".parquet";
        try {
            this.parquetWriter = createParquetWriter();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create Parquet writer", e);
        }
    }

    public ParquetFileQueueEventRepo(String filePath) {
        this.eventQueue = new LinkedBlockingQueue<>();
        this.parquetFilePath = filePath;
        try {
            this.parquetWriter = createParquetWriter();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create Parquet writer", e);
        }
    }

    private ParquetWriter<Event<T>> createParquetWriter() throws IOException {
        Configuration conf = new Configuration();
        Path path = new Path(parquetFilePath);

        // 创建 Avro schema
        Schema schema = Schema.createRecord("Event", "An event with type and payload",
                "com.tanggo.fund.jnautilustrader.core.entity", false);
        schema.setFields(java.util.Arrays.asList(
                new Schema.Field("type", Schema.create(Schema.Type.STRING), "Event type", null),
                new Schema.Field("payload", Schema.create(Schema.Type.STRING), "Event payload", null)
        ));

        return AvroParquetWriter.<Event<T>>builder(path)
                .withCompressionCodec(CompressionCodecName.SNAPPY)
                .withPageSize(1024 * 1024)
                .withRowGroupSize(8 * 1024 * 1024)
                .withSchema(schema)
                .build();
    }

    private synchronized void initializeReader() {
        if (!readerInitialized) {
            try {
                Configuration conf = new Configuration();
                Path path = new Path(parquetFilePath);
                this.parquetReader = AvroParquetReader.<Event<T>>builder(path).build();
                readerInitialized = true;
            } catch (IOException e) {
                throw new RuntimeException("Failed to create Parquet reader", e);
            }
        }
    }

    /**
     * 接收事件（从队列头中取出直到文件尾）
     */
    @Override
    public Event<T> receive() {
        // 首先尝试从内存队列中获取
        Event<T> event = eventQueue.poll();
        if (event != null) {
            return event;
        }

        // 内存队列空时，尝试从Parquet文件中读取
        initializeReader();
        try {
            return parquetReader.read();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read event from Parquet file", e);
        }
    }

    /**
     * 发送事件（添加到队列-文件尾）
     */
    @Override
    public boolean send(Event<T> event) {
        try {
            // 同时添加到内存队列和Parquet文件
            eventQueue.offer(event);
            parquetWriter.write(event);
            return true;
        } catch (IOException e) {
            throw new RuntimeException("Failed to write event to Parquet file", e);
        }
    }

    /**
     * 获取队列大小
     */
    public int getQueueSize() {
        return eventQueue.size();
    }

    /**
     * 获取Parquet文件路径
     */
    public String getParquetFilePath() {
        return parquetFilePath;
    }

    /**
     * 关闭资源
     */
    @Override
    public void close() {
        try {
            if (parquetWriter != null) {
                parquetWriter.close();
            }
            if (parquetReader != null) {
                parquetReader.close();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to close Parquet resources", e);
        }
    }
}
