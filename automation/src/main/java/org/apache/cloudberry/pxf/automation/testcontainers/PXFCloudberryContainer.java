package org.apache.cloudberry.pxf.automation.testcontainers;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.Frame;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * PXF + Cloudberry colocated testcontainer.
 *
 * Cloudberry is built during image creation.
 * Demo cluster and PXF are initialised at runtime via {@code entrypoint.sh}.
 *
 * Use {@link #getInstance()} to get a singleton that is started once per
 * automation JVM. The container shares a Docker {@link Network} with other
 * test containers so they can communicate by hostname.
 */
public class PXFCloudberryContainer extends GenericContainer<PXFCloudberryContainer> {

    private static final Map<String, String> BASE_IMAGES = new HashMap<>();
    static {
        BASE_IMAGES.put("ubuntu", "apache/incubator-cloudberry:cbdb-build-ubuntu22.04-latest");
        BASE_IMAGES.put("rocky9", "apache/incubator-cloudberry:cbdb-build-rocky9-latest");
    }

    public static final int CLOUDBERRY_PORT = 7000;
    public static final int PXF_PORT = 5888;
    public static final String CLOUDBERRY_USER = "gpadmin";

    private static final String CONTAINER_GRADLE_RO_CACHE = "/home/gpadmin/.gradle-host-cache";
    private static final String CONTAINER_REPO_DIR = "/home/gpadmin/workspace/cloudberry-pxf";
    private static final String CONTAINER_SCRIPT_DIR =
            CONTAINER_REPO_DIR + "/automation/src/main/resources/testcontainers/pxf-cbdb/script";

    /* files required by `server`/`fdw`/`external-table` Makefiles. */
    private static final String[] HOST_ROOT_FILES = {"version", "api_version", "common.mk"};

    private static final Network network = Network.newNetwork();
    private static PXFCloudberryContainer instance;

    private PXFCloudberryContainer(String imageName, String repoPath) {
        super(DockerImageName.parse(imageName));
        Path root = Paths.get(repoPath).toAbsolutePath().normalize();

        withNetwork(network)
            .withNetworkAliases("mdw")
            .withExposedPorts(CLOUDBERRY_PORT, PXF_PORT)
            .withCommand("tail", "-f", "/dev/null")
            .withCreateContainerCmdModifier(cmd -> cmd.withHostName("mdw"))
            .waitingFor(new AbstractWaitStrategy() {
                @Override
                protected void waitUntilReady() {
                    // No-op: we shouldn't wait for processes to run here
                    // will start applications with entrypoint.sh
                }
            })
            .withStartupTimeout(Duration.ofMinutes(25))
            // Copy directories to the container at runtime:
            .withCopyToContainer(
                    MountableFile.forHostPath(root.resolve("external-table").toString()),
                    CONTAINER_REPO_DIR + "/external-table")
            .withCopyToContainer(
                    MountableFile.forHostPath(root.resolve("fdw").toString()),
                    CONTAINER_REPO_DIR + "/fdw")
            .withCopyToContainer(
                    MountableFile.forHostPath(root.resolve("server").toString()),
                    CONTAINER_REPO_DIR + "/server")
            .withCopyToContainer(
                    MountableFile.forHostPath(root.resolve("automation").toString()),
                    CONTAINER_REPO_DIR + "/automation");
        // Copy required files to the container at runtime:
        for (String name : HOST_ROOT_FILES) {
            withCopyToContainer(
                    MountableFile.forHostPath(root.resolve(name).toString()),
                    CONTAINER_REPO_DIR + "/" + name);
        }

        // mount /home/username/.gradle/caches to the container to speed up build
        Path hostGradleCache = Paths.get(System.getProperty("user.home"), ".gradle", "caches");
        boolean hasHostGradleCache = Files.exists(hostGradleCache, LinkOption.NOFOLLOW_LINKS);
        if (hasHostGradleCache) {
            withFileSystemBind(hostGradleCache.toString(), CONTAINER_GRADLE_RO_CACHE, BindMode.READ_ONLY);
            withEnv("GRADLE_RO_DEP_CACHE", CONTAINER_GRADLE_RO_CACHE);
        }
    }

    private static String resolveDistro() {
        String prop = System.getProperty("pxf.test.distro");
        if (prop != null && !prop.isEmpty()) return prop;
        String env = System.getenv("PXF_TEST_DISTRO");
        if (env != null && !env.isEmpty()) return env;
        return "ubuntu";
    }

    /**
     * Returns a singleton container, starting it and running the environment
     * setup on first access. Thread-safe.
     */
    public static synchronized PXFCloudberryContainer getInstance() {
        if (instance == null) {
            String repo = resolveProperty("pxf.test.repo.path", findRepoPath());
            String distro = resolveDistro();
            String imageName = "pxf/cbdb-testcontainer-" + distro + ":1";
            String baseImage = BASE_IMAGES.getOrDefault(distro, BASE_IMAGES.get("ubuntu"));

            ClasspathDockerContainerBuilder.ensureImageExists(
                    imageName,
                    "testcontainers/pxf-cbdb/",
                    new String[]{
                            "Dockerfile",
                            "script/build_cloudberry.sh"
                    },
                    new String[]{"BASE_IMAGE=" + baseImage});

            instance = new PXFCloudberryContainer(imageName, repo);
            instance.start();
            Runtime.getRuntime().addShutdownHook(new Thread(instance::stop));

            try {
                instance.runEntrypoint();
                instance.waitingFor(Wait.forListeningPorts(CLOUDBERRY_PORT, PXF_PORT));
            } catch (Exception e) {
                instance.stop();
                instance = null;
                throw new RuntimeException("Failed to initialize PXF container", e);
            }
        }
        return instance;
    }


    private void runEntrypoint() throws IOException, InterruptedException {
        logger().info("Running entrypoint.sh inside container (this takes several minutes)...");
        int exitCode = execInContainerWithLiveOutput(
                "bash", "-l", "-c", CONTAINER_SCRIPT_DIR + "/entrypoint.sh 2>&1");
        if (exitCode != 0) {
            throw new RuntimeException("entrypoint.sh failed (exit " + exitCode + ")");
        }
        logger().info("entrypoint.sh completed successfully");
    }

    private int execInContainerWithLiveOutput(String... command) throws InterruptedException {
        DockerClient client = DockerClientFactory.instance().client();
        ExecCreateCmdResponse exec = client.execCreateCmd(getContainerId())
                .withCmd(command)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .exec();

        client.execStartCmd(exec.getId())
                .exec(new ResultCallback.Adapter<Frame>() {
                    @Override
                    public void onNext(Frame frame) {
                        System.out.print(new String(frame.getPayload(), StandardCharsets.UTF_8));
                    }
                })
                .awaitCompletion();

        Long exitCode = client.inspectExecCmd(exec.getId()).exec().getExitCodeLong();
        return exitCode != null ? exitCode.intValue() : -1;
    }

    private static String resolveProperty(String key, String fallback) {
        String value = System.getProperty(key);
        return (value != null && !value.isEmpty()) ? value : fallback;
    }

    private static String findRepoPath() {
        File dir = new File(System.getProperty("user.dir"));
        for (int i = 0; i < 5; i++) {
            if (new File(dir, "automation/pom.xml").exists()) {
                return dir.getAbsolutePath();
            }
            dir = dir.getParentFile();
            if (dir == null)
                break;
        }
        throw new IllegalStateException(
                "Cannot auto-detect cloudberry-pxf repo root. Set -Dpxf.test.repo.path=...");
    }


    public Network getSharedNetwork() {
        return network;
    }

    public int getCloudberryMappedPort() {
        return getMappedPort(CLOUDBERRY_PORT);
    }

    public int getCloudberryInternalPort() {
        return CLOUDBERRY_PORT;
    }

    public String getCloudberryUser() {
        return CLOUDBERRY_USER;
    }

    public String getPxfInternalHost() {
        return "localhost";
    }

    public int getPxfInternalPort() {
        return PXF_PORT;
    }

}
