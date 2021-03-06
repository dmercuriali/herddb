/*
 Licensed to Diennea S.r.l. under one
 or more contributor license agreements. See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership. Diennea S.r.l. licenses this file
 to you under the Apache License, Version 2.0 (the
 "License"); you may not use this file except in compliance
 with the License.  You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an
 "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 KIND, either express or implied.  See the License for the
 specific language governing permissions and limitations
 under the License.

 */

package herddb.server;

import herddb.client.ClientConfiguration;
import herddb.client.HDBClient;
import herddb.client.HDBConnection;
import herddb.client.ScanResultSet;
import herddb.client.ZookeeperClientSideMetadataProvider;
import herddb.model.TableSpace;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.Properties;
import org.apache.curator.test.TestingServer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * SimpleServer boot test
 *
 * @author enrico.olivelli
 */
public class SimpleClusterTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void test() throws Exception {

        try (TestingServer zookeeperServer = new TestingServer(-1, folder.newFolder("zk"));) {

            File tmpConfFile = folder.newFile("test.server_cluster.properties");

            try (InputStream in = SimpleClusterTest.class.getResourceAsStream("/conf/test.server_cluster.properties")) {
                Properties props = new Properties();
                props.load(in);
                props.put(ServerConfiguration.PROPERTY_BASEDIR, folder.newFolder().getAbsolutePath());
                props.put(ServerConfiguration.PROPERTY_ZOOKEEPER_ADDRESS, zookeeperServer.getConnectString());
                try (FileOutputStream oo = new FileOutputStream(tmpConfFile)) {
                    props.store(oo, "");
                }
            }
            Thread runner = new Thread(() -> {
                ServerMain.main(tmpConfFile.getAbsolutePath());
            });
            runner.start();
            while (ServerMain.getRunningInstance() == null
                    || !ServerMain.getRunningInstance().isStarted()) {
                Thread.sleep(1000);
                System.out.println("waiting for boot");
            }

            ServerMain.getRunningInstance().getServer().waitForStandaloneBoot();

            try (HDBClient client = new HDBClient(new ClientConfiguration(folder.newFolder().toPath()))) {
                client.setClientSideMetadataProvider(
                        new ZookeeperClientSideMetadataProvider(zookeeperServer.getConnectString(), 40000, "/herddb")
                );
                try (HDBConnection con = client.openConnection()) {
                    try (ScanResultSet scan = con.executeScan(TableSpace.DEFAULT, "SELECT * FROM SYSTABLES", false, Collections.emptyList(), 0, 10, 10, false);) {
                        scan.consume();
                    }
                }
            }
            ServerMain.getRunningInstance().close();
        } finally {
            if (ServerMain.getRunningInstance() != null) {
                ServerMain.getRunningInstance().close();
            }
        }
    }
}
