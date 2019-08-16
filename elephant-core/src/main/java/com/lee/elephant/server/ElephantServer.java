package com.lee.elephant.server;

import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;
import org.postgresql.PGProperty;
import org.postgresql.replication.PGReplicationStream;

import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * @author liwei
 * @date 2019-08-14 15:18
 */
@Slf4j
public class ElephantServer {

    private final static String SLOT_NAME = "slot_for_kafka";

    public static void main(String[] args) throws SQLException {
        String url = "jdbc:postgresql://localhost:5432/user";
        Properties props = new Properties();
        PGProperty.USER.set(props, "liwei");
        PGProperty.PASSWORD.set(props, "123456");
        PGProperty.ASSUME_MIN_SERVER_VERSION.set(props, "9.4");
        PGProperty.REPLICATION.set(props, "database");
        PGProperty.PREFER_QUERY_MODE.set(props, "simple");

        Connection sqlConnection = DriverManager.getConnection(url, props);
        PGConnection replConnection = sqlConnection.unwrap(PGConnection.class);

        createReplSlot(replConnection, SLOT_NAME);

        PGReplicationStream stream = createReplStream(replConnection, SLOT_NAME);

        while (true) {
            //non blocking receive message
            ByteBuffer msg = stream.readPending();

            if (msg == null) {
                try {
                    TimeUnit.MILLISECONDS.sleep(10L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                continue;
            }

            int offset = msg.arrayOffset();
            byte[] source = msg.array();
            int length = source.length - offset;
            log.info("received message：" + new String(source, offset, length));

            //feedback
            stream.setAppliedLSN(stream.getLastReceiveLSN());
            stream.setFlushedLSN(stream.getLastReceiveLSN());
        }
    }

    private static PGReplicationStream createReplStream(PGConnection replConnection, String slotName) throws SQLException {
        return replConnection.getReplicationAPI()
                .replicationStream()
                .logical()
                .withSlotName(slotName)
                .withSlotOption("include-xids", true)
                .withSlotOption("skip-empty-xacts", true)
                .withStatusInterval(20, TimeUnit.SECONDS)
                .start();
    }

    private static void createReplSlot(PGConnection replConnection, String slotName) throws SQLException {
        try {
            replConnection.getReplicationAPI()
                    .createReplicationSlot()
                    .logical()
                    .withSlotName(slotName)
                    .withOutputPlugin("test_decoding")
                    .make();
        } catch (SQLException e) {
            String msg = "ERROR: replication slot \"" + slotName + "\" already exists";
            if (msg.equals(e.getMessage())) {
                log.warn(msg);
                return;
            }
            throw e;
        }
    }
}
