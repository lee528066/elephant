package com.lee.elephant.db;

import org.junit.Test;
import org.postgresql.PGProperty;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * @author liwei
 * @date 2019-08-15 10:41
 */
public class MockDbChangesTest {

    @Test
    public void testConcurrentDbChanges() {
        String url = "jdbc:postgresql://localhost:5432/user";
        Properties props = new Properties();
        PGProperty.USER.set(props, "liwei");
        PGProperty.PASSWORD.set(props, "123456");
        PGProperty.ASSUME_MIN_SERVER_VERSION.set(props, "9.4");
        PGProperty.REPLICATION.set(props, "database");
        PGProperty.PREFER_QUERY_MODE.set(props, "simple");

        AtomicInteger succCount = new AtomicInteger(0);

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(100);

        ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        for (int i = 0; i < 100; i++) {
            int index = i;
            pool.execute(() -> {
                System.out.println("current task is:" + index);
                Connection sqlConnection = null;
                Statement statement = null;
                try {
                    startGate.await();
                    sqlConnection = DriverManager.getConnection(url, props);
                    statement = sqlConnection.createStatement();
                    statement.execute("insert into student(name, age) values('liwei" + index + "', " + index + 10 + ")");
                } catch (Exception e) {
                    e.printStackTrace();
                }finally {
                    releaseResource(sqlConnection, statement);
                    endGate.countDown();
                }
                succCount.incrementAndGet();
            });
        }

        startGate.countDown();
        try {
            endGate.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void releaseResource(Connection sqlConnection, Statement statement) {
        try {
            if (statement != null) {
                statement.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        if (sqlConnection != null) {
            try {
                sqlConnection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}
